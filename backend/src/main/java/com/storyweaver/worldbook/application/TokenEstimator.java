package com.storyweaver.worldbook.application;

import org.springframework.stereotype.Component;

@Component
public class TokenEstimator {
    public int estimate(String title, String content) {
        int codePoints = title.codePointCount(0, title.length()) + content.codePointCount(0, content.length());
        return Math.max(1, (codePoints + 1) / 2 + 4);
    }
}
