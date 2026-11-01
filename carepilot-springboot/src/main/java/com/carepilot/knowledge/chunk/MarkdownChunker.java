package com.carepilot.knowledge.chunk;

import com.carepilot.knowledge.RagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 父子块切分：按 Markdown 标题切「父块」，父块内再滑窗切「子块」。
 * 检索命中子块，回答时把父块一起给模型，兼顾精度和上下文完整。
 */
@Component
@RequiredArgsConstructor
public class MarkdownChunker {

    private final RagProperties props;

    public record Child(int no, int parentNo, String titlePath, String text, String parentText) {}

    public List<Child> split(String markdown) {
        List<String[]> sections = sections(markdown == null ? "" : markdown);
        List<Child> out = new ArrayList<>();
        int childNo = 0;
        for (int p = 0; p < sections.size(); p++) {
            String title = sections.get(p)[0];
            String body = sections.get(p)[1];
            String parentText = (title.isEmpty() ? "" : title + "\n") + body;
            if (parentText.isBlank()) continue;
            int size = Math.max(120, props.getChunkSize());
            int overlap = Math.max(0, Math.min(props.getChunkOverlap(), size / 2));
            if (parentText.length() <= size) {
                out.add(new Child(childNo++, p, title, parentText, parentText));
                continue;
            }
            int start = 0;
            while (start < parentText.length()) {
                int end = Math.min(parentText.length(), start + size);
                String piece = parentText.substring(start, end).trim();
                if (!piece.isEmpty()) {
                    out.add(new Child(childNo++, p, title, piece, parentText));
                }
                if (end == parentText.length()) break;
                start = Math.max(start + 1, end - overlap);
            }
        }
        return out;
    }

    private List<String[]> sections(String md) {
        List<String[]> out = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        String title = "";
        for (String line : md.split("\n")) {
            boolean isHeading = line.startsWith("#");
            if (isHeading) {
                if (!body.toString().isBlank() || !title.isEmpty()) {
                    out.add(new String[]{title, body.toString().trim()});
                }
                title = line.replaceAll("^#+\\s*", "").trim();
                body.setLength(0);
            } else {
                body.append(line).append("\n");
            }
        }
        if (!body.toString().isBlank() || !title.isEmpty()) {
            out.add(new String[]{title, body.toString().trim()});
        }
        return out;
    }
}
