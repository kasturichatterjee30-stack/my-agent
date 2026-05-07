package com.agent.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ObsidianTool {

    private final Path vaultPath;

    public ObsidianTool(@Value("${obsidian.vault.path}") String vaultPath) {
        this.vaultPath = Paths.get(vaultPath);
    }

    public String execute(String operation, Map<String, Object> input) {
        return switch (operation) {
            case "list_notes"     -> listNotes((String) input.getOrDefault("folder", ""));
            case "read_note"      -> readNote((String) input.get("path"));
            case "search_notes"   -> searchNotes((String) input.get("keyword"));
            case "get_daily_note" -> getDailyNote();
            case "get_recent_notes" -> getRecentNotes();
            default -> "Unknown operation: " + operation;
        };
    }

    private String listNotes(String folder) {
        Path target = folder.isEmpty() ? vaultPath : vaultPath.resolve(folder);
        try {
            List<String> notes = Files.walk(target, 2)
                    .filter(p -> p.toString().endsWith(".md"))
                    .map(p -> vaultPath.relativize(p).toString())
                    .sorted()
                    .collect(Collectors.toList());
            return notes.isEmpty() ? "No notes found." : String.join("\n", notes);
        } catch (IOException e) {
            return "Error listing notes: " + e.getMessage();
        }
    }

    private String readNote(String path) {
        if (path == null) return "Error: path is required for read_note";
        try {
            return Files.readString(vaultPath.resolve(path));
        } catch (IOException e) {
            return "Error reading note: " + e.getMessage();
        }
    }

    private String searchNotes(String keyword) {
        if (keyword == null) return "Error: keyword is required for search_notes";
        List<String> matches = new ArrayList<>();
        try {
            Files.walkFileTree(vaultPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".md")) {
                        String content = Files.readString(file);
                        if (content.toLowerCase().contains(keyword.toLowerCase())) {
                            matches.add(vaultPath.relativize(file).toString());
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "Error searching notes: " + e.getMessage();
        }
        return matches.isEmpty()
                ? "No notes found containing: " + keyword
                : "Found in " + matches.size() + " note(s):\n" + String.join("\n", matches);
    }

    private String getDailyNote() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path dailyDir = vaultPath.resolve("01 Daily");
        try {
            Optional<Path> note = Files.list(dailyDir)
                    .filter(p -> p.getFileName().toString().startsWith(today))
                    .findFirst();
            return note.isPresent()
                    ? Files.readString(note.get())
                    : "No daily note found for today (" + today + ").";
        } catch (IOException e) {
            return "Error reading daily note: " + e.getMessage();
        }
    }

    private String getRecentNotes() {
        Instant cutoff = Instant.now().minusSeconds(7L * 24 * 60 * 60);
        List<String> recent = new ArrayList<>();
        try {
            Files.walkFileTree(vaultPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".md") && attrs.lastModifiedTime().toInstant().isAfter(cutoff)) {
                        recent.add(vaultPath.relativize(file).toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "Error getting recent notes: " + e.getMessage();
        }
        recent.sort(Comparator.naturalOrder());
        return recent.isEmpty()
                ? "No notes modified in the last 7 days."
                : "Notes modified in last 7 days:\n" + String.join("\n", recent);
    }
}
