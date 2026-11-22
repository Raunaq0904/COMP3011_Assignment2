/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 *
 * AI acknowledgement: this file was developed with the assistance of Claude (Anthropic's generative AI
 * assistant), used as a research, design and development aid. Claude proposed the design and the code,
 * explaining each part step by step. I typed every change in myself, then ran, tested and reviewed it so
 * that I understand what each part does and why it is in the project. All commits were made by me.
 * See the "AI acknowledgement" section of README.md for details.
 */
package comp3011;

import java.util.ArrayList;
import java.util.List;

/**
 * Text rules for subtitles: at most {@value #MAX_LINE_CHARS} characters per line and at most {@value #MAX_LINES} lines
 * on screen, breaking after punctuation where possible. Pure functions, so they are easy to test and safe to call from
 * any thread.
 */
public final class SubtitleText {

    public static final int MAX_LINE_CHARS = 42;
    public static final int MAX_LINES = 2;

    private static final String PHRASE_END = ".,;:!?\u2026";

    private SubtitleText() {
    }

    /**
     * Splits speech into chunks that each fit on at most two lines. Whole phrases are kept together where possible;
     * a phrase is only cut when it cannot fit on two lines by itself.
     */
    public static List<String> chunks(String text) {
        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (List<String> phrase : phrases(words(text))) {
            List<String> combined = new ArrayList<>(current);
            combined.addAll(phrase);
            if (greedyLines(combined).size() <= MAX_LINES) {
                current = combined;
                continue;
            }
            flush(chunks, current);
            for (String word : phrase) {
                current.add(word);
                if (greedyLines(current).size() > MAX_LINES) {
                    current.remove(current.size() - 1);
                    flush(chunks, current);
                    current.add(word);
                }
            }
        }
        flush(chunks, current);
        return chunks;
    }

    /**
     * Lays one chunk out as one line if it fits, otherwise as two lines. The break goes after punctuation if a valid
     * one exists, and otherwise where the two lines are most even.
     */
    public static List<String> lines(String chunk) {
        List<String> words = words(chunk);
        if (words.isEmpty()) {
            return List.of();
        }
        String whole = String.join(" ", words);
        if (whole.length() <= MAX_LINE_CHARS) {
            return List.of(whole);
        }

        int bestSplit = -1;
        boolean bestAfterPunctuation = false;
        int bestGap = Integer.MAX_VALUE;
        int leftLength = 0;
        for (int split = 1; split < words.size(); split++) {
            leftLength += (split > 1 ? 1 : 0) + words.get(split - 1).length();
            int rightLength = whole.length() - leftLength - 1;
            if (leftLength > MAX_LINE_CHARS || rightLength > MAX_LINE_CHARS) {
                continue;
            }
            boolean afterPunctuation = endsPhrase(words.get(split - 1));
            int gap = Math.abs(leftLength - rightLength);
            boolean better = (afterPunctuation && !bestAfterPunctuation)
                    || (afterPunctuation == bestAfterPunctuation && gap < bestGap);
            if (better) {
                bestSplit = split;
                bestAfterPunctuation = afterPunctuation;
                bestGap = gap;
            }
        }
        if (bestSplit < 0) {
            return greedyLines(words);
        }
        return List.of(String.join(" ", words.subList(0, bestSplit)),
                String.join(" ", words.subList(bestSplit, words.size())));
    }

    /** Splits on whitespace, and cuts any word longer than one line into line-sized pieces. */
    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        for (String word : text.trim().split("\\s+")) {
            for (int start = 0; start < word.length(); start += MAX_LINE_CHARS) {
                words.add(word.substring(start, Math.min(word.length(), start + MAX_LINE_CHARS)));
            }
        }
        return words;
    }

    /** Groups words into phrases, each ending at a word that ends with punctuation. */
    private static List<List<String>> phrases(List<String> words) {
        List<List<String>> phrases = new ArrayList<>();
        List<String> phrase = new ArrayList<>();
        for (String word : words) {
            phrase.add(word);
            if (endsPhrase(word)) {
                phrases.add(phrase);
                phrase = new ArrayList<>();
            }
        }
        if (!phrase.isEmpty()) {
            phrases.add(phrase);
        }
        return phrases;
    }

    private static boolean endsPhrase(String word) {
        return PHRASE_END.indexOf(word.charAt(word.length() - 1)) >= 0;
    }

    /** Ordinary word wrapping: fill each line as far as it will go. */
    private static List<String> greedyLines(List<String> words) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() > 0 && line.length() + 1 + word.length() > MAX_LINE_CHARS) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static void flush(List<String> chunks, List<String> current) {
        if (!current.isEmpty()) {
            chunks.add(String.join(" ", current));
            current.clear();
        }
    }
}