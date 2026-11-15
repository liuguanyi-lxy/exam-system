package com.atguigu.exam.vo;

/**
 * 判卷结果内部类
 */
public  class GradingResult {
    //实际得分
    private Integer score;
    //具体的评价反馈
    private String feedback;
    //扣分原因或得分依据
    private String reason;

    public GradingResult(Integer score, String feedback, String reason) {
        this.score = score;
        this.feedback = feedback;
        this.reason = reason;
    }

    // Getters
    public Integer getScore() { return score; }
    public String getFeedback() { return feedback; }
    public String getReason() { return reason; }
}