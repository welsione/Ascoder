package cn.welsione.ascoder.agent.extension.skill;

import lombok.AllArgsConstructor;
import lombok.Value;

/** 内置 Skill 的不可变定义，包含名称、描述和 Skill 内容。 */
@Value
@AllArgsConstructor
public class BuiltinSkillDefinition {
    String name;
    String description;
    String skillContent;
}
