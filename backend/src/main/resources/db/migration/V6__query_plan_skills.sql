alter table queryPlans
    add column recommendedSkillsJson text null comment '推荐使用的 Skill 列表，JSON 数组格式' after recommendedToolsJson;

update queryPlans
set recommendedSkillsJson = '[]'
where recommendedSkillsJson is null;

alter table queryPlans
    modify column recommendedSkillsJson text not null comment '推荐使用的 Skill 列表，JSON 数组格式';
