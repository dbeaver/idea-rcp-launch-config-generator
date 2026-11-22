/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2026 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.tools.rcplaunchconfig.github;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class GitHubTicketBranchResolver {
    private static final String DEFAULT_GRAPHQL_ENDPOINT = "https://api.github.com/graphql";

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || args[0].isBlank()) {
            System.err.println("Usage: GitHubTicketBranchResolver <github-ticket-url>");
            System.exit(2);
        }

        String token = firstNotBlank(System.getenv("GITHUB_TOKEN"), System.getenv("GH_TOKEN"));
        if (token == null) {
            System.err.println("GITHUB_TOKEN or GH_TOKEN environment variable is required");
            System.exit(2);
        }

        Ticket ticket = Ticket.parse(args[0]);
        Set<BranchRef> branches = new GitHubTicketBranchResolver().resolve(ticket, token);
        for (BranchRef branch : branches) {
            System.out.println(branch.repository() + "\t" + branch.branch());
        }
    }

    private Set<BranchRef> resolve(Ticket ticket, String token) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        Set<BranchRef> branches = new LinkedHashSet<>();
        String branchesCursor = null;
        String timelineCursor = null;
        boolean hasNextBranches;
        boolean hasNextTimeline;

        do {
            Map<String, Object> response = executeGraphQL(client, token, ticket, branchesCursor, timelineCursor);
            checkErrors(response);

            Map<String, Object> issue = objectPath(response, "data", "repository", "issue");
            if (issue == null) {
                throw new IllegalStateException("GitHub issue was not found: " + ticket.url());
            }

            Map<String, Object> linkedBranches = object(issue.get("linkedBranches"));
            if (linkedBranches != null) {
                collectLinkedBranches(linkedBranches, branches);
            }

            Map<String, Object> timelineItems = object(issue.get("timelineItems"));
            if (timelineItems != null) {
                collectPullRequestBranches(timelineItems, branches);
            }

            Map<String, Object> branchesPageInfo = objectPath(linkedBranches, "pageInfo");
            hasNextBranches = Boolean.TRUE.equals(value(branchesPageInfo, "hasNextPage"));
            branchesCursor = stringValue(value(branchesPageInfo, "endCursor"));

            Map<String, Object> timelinePageInfo = objectPath(timelineItems, "pageInfo");
            hasNextTimeline = Boolean.TRUE.equals(value(timelinePageInfo, "hasNextPage"));
            timelineCursor = stringValue(value(timelinePageInfo, "endCursor"));
        } while (hasNextBranches || hasNextTimeline);

        return branches;
    }

    private Map<String, Object> executeGraphQL(
        HttpClient client,
        String token,
        Ticket ticket,
        String branchesCursor,
        String timelineCursor
    ) throws IOException, InterruptedException {
        String body = "{"
            + "\"query\":\"" + escapeJson(graphQLQuery()) + "\","
            + "\"variables\":{"
            + "\"owner\":\"" + escapeJson(ticket.owner()) + "\","
            + "\"name\":\"" + escapeJson(ticket.repository()) + "\","
            + "\"number\":" + ticket.number() + ","
            + "\"branchesAfter\":" + jsonStringOrNull(branchesCursor) + ","
            + "\"timelineAfter\":" + jsonStringOrNull(timelineCursor)
            + "}"
            + "}";

        HttpRequest request = HttpRequest.newBuilder(URI.create(graphQLEndpoint()))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("GitHub API request failed with HTTP " + response.statusCode() + ": " + response.body());
        }

        return object(new JsonParser(response.body()).parse());
    }

    private static String graphQLQuery() {
        return "query($owner:String!,$name:String!,$number:Int!,$branchesAfter:String,$timelineAfter:String){"
            + "repository(owner:$owner,name:$name){"
            + "issue(number:$number){"
            + "linkedBranches(first:100,after:$branchesAfter){"
            + "pageInfo{hasNextPage endCursor}"
            + "nodes{ref{name repository{nameWithOwner}}}"
            + "}"
            + "timelineItems(first:100,after:$timelineAfter,itemTypes:[CONNECTED_EVENT,CROSS_REFERENCED_EVENT]){"
            + "pageInfo{hasNextPage endCursor}"
            + "nodes{"
            + "__typename "
            + "... on ConnectedEvent{subject{__typename ... on PullRequest{headRefName headRepository{nameWithOwner}}}}"
            + "... on CrossReferencedEvent{source{__typename ... on PullRequest{headRefName headRepository{nameWithOwner}}}}"
            + "}"
            + "}"
            + "}"
            + "}"
            + "}";
    }

    private static void collectLinkedBranches(Map<String, Object> linkedBranches, Set<BranchRef> branches) {
        for (Object nodeObject : list(linkedBranches.get("nodes"))) {
            Map<String, Object> ref = objectPath(object(nodeObject), "ref");
            Map<String, Object> repository = objectPath(ref, "repository");
            addBranch(branches, stringValue(value(repository, "nameWithOwner")), stringValue(value(ref, "name")));
        }
    }

    private static void collectPullRequestBranches(Map<String, Object> timelineItems, Set<BranchRef> branches) {
        for (Object nodeObject : list(timelineItems.get("nodes"))) {
            Map<String, Object> node = object(nodeObject);
            Map<String, Object> pullRequest = objectPath(node, "subject");
            if (pullRequest == null) {
                pullRequest = objectPath(node, "source");
            }
            if (!"PullRequest".equals(stringValue(value(pullRequest, "__typename")))) {
                continue;
            }
            Map<String, Object> headRepository = objectPath(pullRequest, "headRepository");
            addBranch(branches, stringValue(value(headRepository, "nameWithOwner")), stringValue(value(pullRequest, "headRefName")));
        }
    }

    private static void addBranch(Set<BranchRef> branches, String repository, String branch) {
        if (repository != null && !repository.isBlank() && branch != null && !branch.isBlank()) {
            branches.add(new BranchRef(repository, branch));
        }
    }

    private static void checkErrors(Map<String, Object> response) {
        List<Object> errors = list(response.get("errors"));
        if (errors.isEmpty()) {
            return;
        }

        List<String> messages = new ArrayList<>();
        for (Object error : errors) {
            String message = stringValue(value(object(error), "message"));
            if (message != null) {
                messages.add(message);
            }
        }
        throw new IllegalStateException("GitHub API returned errors: " + String.join("; ", messages));
    }

    private static String graphQLEndpoint() {
        String endpoint = firstNotBlank(System.getenv("GITHUB_GRAPHQL_URL"));
        if (endpoint != null) {
            return endpoint;
        }
        String apiUrl = firstNotBlank(System.getenv("GITHUB_API_URL"));
        if (apiUrl != null) {
            return apiUrl.replaceFirst("/+$", "") + "/graphql";
        }
        return DEFAULT_GRAPHQL_ENDPOINT;
    }

    private static String jsonStringOrNull(String value) {
        return value == null ? "null" : "\"" + escapeJson(value) + "\"";
    }

    private static String escapeJson(String value) {
        StringBuilder result = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> result.append(ch);
            }
        }
        return result.toString();
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static Map<String, Object> objectPath(Map<String, Object> object, String... path) {
        Map<String, Object> current = object;
        for (String element : path) {
            if (current == null) {
                return null;
            }
            current = object(current.get(element));
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }

    private static Object value(Map<String, Object> object, String name) {
        return object == null ? null : object.get(name);
    }

    private static String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private record Ticket(String owner, String repository, int number, String url) {
        private static Ticket parse(String value) throws URISyntaxException {
            URI uri = new URI(value);
            String[] parts = uri.getPath().split("/");
            if (parts.length < 5 || !"issues".equals(parts[3].toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Expected GitHub issue URL, for example https://github.com/owner/repository/issues/123");
            }
            try {
                return new Ticket(parts[1], parts[2], Integer.parseInt(parts[4]), value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("GitHub issue URL does not contain a valid issue number: " + value, exception);
            }
        }
    }

    private record BranchRef(String repository, String branch) {
        private BranchRef {
            Objects.requireNonNull(repository);
            Objects.requireNonNull(branch);
        }
    }

    private static class JsonParser {
        private final String text;
        private int position;

        private JsonParser(String text) {
            this.text = text;
        }

        private Object parse() {
            Object value = parseValue();
            skipWhitespace();
            if (position != text.length()) {
                throw error("Unexpected trailing JSON content");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (position >= text.length()) {
                throw error("Unexpected end of JSON");
            }
            char ch = text.charAt(position);
            return switch (ch) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseKeyword("true", Boolean.TRUE);
                case 'f' -> parseKeyword("false", Boolean.FALSE);
                case 'n' -> parseKeyword("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (consume('}')) {
                return result;
            }
            do {
                String name = parseString();
                skipWhitespace();
                expect(':');
                result.put(name, parseValue());
                skipWhitespace();
            } while (consume(','));
            expect('}');
            return result;
        }

        private List<Object> parseArray() {
            List<Object> result = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            do {
                result.add(parseValue());
                skipWhitespace();
            } while (consume(','));
            expect(']');
            return result;
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (position < text.length()) {
                char ch = text.charAt(position++);
                if (ch == '"') {
                    return result.toString();
                }
                if (ch != '\\') {
                    result.append(ch);
                    continue;
                }
                if (position >= text.length()) {
                    throw error("Unexpected end of escaped JSON string");
                }
                char escaped = text.charAt(position++);
                switch (escaped) {
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case '/' -> result.append('/');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append(parseUnicodeEscape());
                    default -> throw error("Unsupported JSON escape: \\" + escaped);
                }
            }
            throw error("Unterminated JSON string");
        }

        private char parseUnicodeEscape() {
            if (position + 4 > text.length()) {
                throw error("Invalid unicode escape");
            }
            int value = Integer.parseInt(text.substring(position, position + 4), 16);
            position += 4;
            return (char) value;
        }

        private Object parseKeyword(String keyword, Object value) {
            if (!text.startsWith(keyword, position)) {
                throw error("Expected JSON keyword: " + keyword);
            }
            position += keyword.length();
            return value;
        }

        private Number parseNumber() {
            int start = position;
            while (position < text.length() && "-+0123456789.eE".indexOf(text.charAt(position)) >= 0) {
                position++;
            }
            if (start == position) {
                throw error("Expected JSON value");
            }
            String value = text.substring(start, position);
            if (value.indexOf('.') >= 0 || value.indexOf('e') >= 0 || value.indexOf('E') >= 0) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        }

        private void skipWhitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        private boolean consume(char expected) {
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw error("Expected '" + expected + "'");
            }
        }

        private IllegalStateException error(String message) {
            return new IllegalStateException(message + " at position " + position);
        }
    }
}
