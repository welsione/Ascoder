alter table queryPlans
    add column confidence double not null default 0.25 comment '规划置信度，范围 0 到 1' after recommendedSkillsJson,
    add column matchedSignalsJson text null comment '命中的规划信号列表，JSON 数组格式' after confidence,
    add column alternativeTypesJson text null comment '候选问题类型列表，JSON 数组格式' after matchedSignalsJson;

update queryPlans
set matchedSignalsJson = '[]',
    alternativeTypesJson = '[]'
where matchedSignalsJson is null
   or alternativeTypesJson is null;

alter table queryPlans
    modify column matchedSignalsJson text not null comment '命中的规划信号列表，JSON 数组格式',
    modify column alternativeTypesJson text not null comment '候选问题类型列表，JSON 数组格式';
