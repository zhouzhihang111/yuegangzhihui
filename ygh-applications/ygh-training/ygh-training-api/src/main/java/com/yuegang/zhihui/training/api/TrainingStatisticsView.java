package com.yuegang.zhihui.training.api;

import java.math.BigDecimal;

public record TrainingStatisticsView(long assigned, long inProgress, long completed, long overdue, BigDecimal completionRate) {}
