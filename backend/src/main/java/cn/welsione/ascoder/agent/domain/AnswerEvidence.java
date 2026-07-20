package cn.welsione.ascoder.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * 回答证据项，记录 CodeGraph 工具返回的关键依据。
 */
@Value
@AllArgsConstructor
public class AnswerEvidence {
    String title;
    String reference;
    String detail;
}
