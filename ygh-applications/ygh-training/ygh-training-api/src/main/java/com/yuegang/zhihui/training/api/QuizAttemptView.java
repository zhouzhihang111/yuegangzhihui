package com.yuegang.zhihui.training.api;

public record QuizAttemptView(String attemptId, String assignmentId, String gateId, int score, boolean passed) {}
