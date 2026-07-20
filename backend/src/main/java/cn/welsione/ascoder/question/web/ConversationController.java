package cn.welsione.ascoder.question.web;

import cn.welsione.ascoder.question.application.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话 REST 控制器。
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        conversationService.delete(id);
    }
}
