package cn.welsione.ascoder.question.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * Agent 事件记录，按 questionId + attemptNo + sequenceNo 隔离，用于事件回放。
 */
@Entity
@Table(name = "agentEvent")
@Getter
@Setter
@NoArgsConstructor
public class AgentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long questionId;

    @Column(nullable = false)
    private int attemptNo = 1;

    @Column(nullable = false)
    private int sequenceNo;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, columnDefinition = "mediumtext")
    private String payload;

    @Column(nullable = false)
    private Date createdAt = new Date();
}
