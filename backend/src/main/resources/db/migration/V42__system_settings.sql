-- 系统运行时设置表：用于在设置页配置 Agent/CodeGraph/Git/QueryPlanner 等运行时参数。
-- 启动期与装配期配置仍由 application.yml / 环境变量承载；本表只承接"运行时行为调参"。
CREATE TABLE systemSettings (
    `key`        VARCHAR(100) NOT NULL PRIMARY KEY,
    value        VARCHAR(500) NOT NULL,
    valueType    VARCHAR(20)  NOT NULL,
    category     VARCHAR(50)  NOT NULL,
    description  VARCHAR(500),
    updatedAt    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_systemSettings_valueType CHECK (valueType IN ('INT','LONG','BOOLEAN','STRING','DOUBLE'))
);

CREATE INDEX idx_systemSettings_category ON systemSettings(category);