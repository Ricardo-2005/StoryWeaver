-- The initial foundation Skill is distilled from the user-supplied local source:
-- D:\写作\SKILL.md, SHA-256 3c09e7a162faa6b5827c541fd7fa21fd388dae7790499be8c32ee3b5c7e06868.
-- Existing projects remain pinned to their previously bound version.

WITH distilled_contract AS (
    SELECT $$
    {
      "identity": {
        "displayName": "中文网文写作基础契约",
        "type": "FOUNDATION",
        "version": "2.0.0",
        "source": "D:\\写作\\SKILL.md"
      },
      "scope": {
        "useWhen": ["续写章节", "按大纲创作章节", "片段润色", "爆款开头", "大纲扩写"],
        "doNotUseWhen": ["学术论文", "法律文书", "未经授权模仿特定在世作者", "覆盖用户确认的正典事实"]
      },
      "inputs": {
        "required": ["taskType", "authorIntent"],
        "optional": ["previousChapter", "chapterOutline", "characterContext", "canonContext", "targetWordCount", "styleRequirements", "forbiddenPhrases", "endingHook"]
      },
      "outputs": {
        "writing": "ChapterDraft",
        "planning": "ScenePlan",
        "revision": "RevisionProposal",
        "review": "ReviewFinding"
      },
      "preconditions": [
        "续写任务优先读取上文和人物当前状态",
        "涉及设定的任务需要可用正典上下文",
        "缺少关键信息时采用最稳妥的网文推进方式，并标注假设"
      ],
      "workflow": [
        "识别任务类型与用户不可覆盖要求",
        "提取本章的冲突、人物主动选择与信息增量",
        "按场景目的安排推进，避免空转日常",
        "以动作、对话、具体物件承载情绪和设定",
        "检查视角、人物口吻、因果、节奏和结尾钩子",
        "扫描反模式并只输出满足约束的结果"
      ],
      "narrativeModels": [
        {"name": "场景必须改变局面", "rule": "每场戏至少推进关系、暴露秘密、制造冲突、改变局势、展示能力、埋伏笔、制造误会或推动主角选择之一"},
        {"name": "渐进式冲突", "rule": "从小异常到明显不对，再到试探、失控、选择和代价或新悬念"},
        {"name": "信息延迟揭示", "rule": "通过对话、动作、文件、道具与异常现象给线索，不一次讲完设定"},
        {"name": "章节钩子", "rule": "结尾优先留下新消息、异常、误会、选择、敌人、能力变化或伏笔回收"}
      ],
      "decisionHeuristics": [
        {"when": "续写上文", "then": "前三段承接上一章的动作、对话、情绪或悬念，不生硬跳场"},
        {"when": "写新章节", "then": "三段内交代主角位置、遭遇、异常与下一步风险"},
        {"when": "写人物情绪", "then": "优先写停顿、视线、动作和具体反应，不贴抽象情绪标签"},
        {"when": "写角色对话", "then": "按身份、性格与压力区分句长和语气，允许省略、停顿、打断和岔题"},
        {"when": "发现日常段落", "then": "删除或让它服务情绪、关系或剧情，不作为空白填充"}
      ],
      "expressionDNA": {
        "priority": ["白描", "具体动作", "具体物件", "自然口语", "中文标点"],
        "styleRules": ["修辞克制，一段最多一两个明显修辞", "环境描写只保留与情绪或异常有关部分", "重要人物、道具和动作可细写，背景快速带过", "情绪优先落在动作与细节上"]
      },
      "constraints": [
        "用户指定剧情、人物关系、字数、禁用句式和风格要求优先级最高",
        "不修改已确认正典，不把候选事实写成事实",
        "不为凑字数增加无关描写",
        "反转必须有前文伏笔和因果"
      ],
      "antiPatterns": [
        "模板化对仗、三段式递进、假转折和总结句收尾",
        "空泛意义膨胀、书面腔、情绪标签化和副词堆砌",
        "旁白代替人物行动、解释性对话、角色语言同质化",
        "生硬转场、平均铺陈、主角一直被事件推着走",
        "禁用句式：不是A而是B、与其说A不如说B、命运的齿轮开始转动等"
      ],
      "honestyBoundaries": [
        "本契约不替代用户提供的正典、人物设定和章节大纲",
        "源材料未覆盖的题材规则只能作为候选建议，不能伪称为确定规范",
        "不精确模仿特定在世作者，不复刻受版权保护的文本风格",
        "文本质量依赖输入上下文；缺少上文时会明确采用的假设"
      ],
      "toolPolicy": {
        "allowed": ["readCanon", "readPreviousChapter", "draft", "review"],
        "forbidden": ["publishContent", "overwriteCanon", "executeImportedScripts"]
      },
      "contextBudget": {"maxInstructionTokens": 6000, "prefer": ["上一章", "本章大纲", "人物状态", "硬规则"]},
      "recovery": {
        "missingContext": "请求补充上文、人物状态或大纲；无法补充时给出带假设的候选推进",
        "constraintConflict": "列出冲突的用户要求与正典/基础契约约束，等待用户取舍"
      },
      "termination": {
        "success": ["完成任务类型要求", "至少一次有效剧情推进", "人物行为与视角一致", "结尾符合任务目标或保留钩子", "反模式扫描无高风险命中"],
        "blocker": ["缺少用户必须确认的正典冲突"]
      },
      "provenance": {
        "generatedBy": "NUWA_STYLE_LOCAL_MATERIAL_DISTILLATION",
        "sourcePath": "D:\\写作\\SKILL.md",
        "sourceSha256": "3c09e7a162faa6b5827c541fd7fa21fd388dae7790499be8c32ee3b5c7e06868",
        "sourceType": "USER_SUPPLIED_LOCAL_SKILL",
        "reviewedByUser": true,
        "distilledAt": "2026-08-07"
      },
      "evaluation": {
        "minimumScore": 85,
        "checks": ["上下文承接", "场景功能", "人物主动性", "口语化差异对话", "AI痕迹规避", "章节钩子"]
      },
      "recommendation": {
        "genres": ["ROMANCE", "REALISTIC_EMOTION", "MYSTERY", "THRILLER", "SCIENCE_FICTION", "WUXIA", "HIGH_CONCEPT", "SPACE_OPERA", "CYBERPUNK", "GAME", "XIANXIA", "HISTORY", "FANTASY", "FANTASY_GENERAL", "URBAN", "CAMPUS", "YOUTH", "FAMILY", "WORKPLACE", "BUSINESS", "MILITARY", "WAR", "APOCALYPSE", "INFINITE_FLOW", "CTHULHU", "DETECTIVE", "WESTERN_FANTASY", "LIGHT_NOVEL", "FAN_FICTION", "CUSTOM"],
        "audiences": ["MALE", "FEMALE", "GENERAL"],
        "perspectives": ["FIRST_PERSON", "THIRD_PERSON"],
        "lengthTypes": ["SHORT_NOVEL", "LONG_NOVEL"]
      }
    }
    $$::jsonb AS payload
)
INSERT INTO global_skill_version (id, global_skill_id, version_no, contract_json, snapshot_hash, status, token_estimate, created_by, created_at)
SELECT
    '00000000-0000-0000-0000-000000000013',
    '00000000-0000-0000-0000-000000000011',
    2,
    payload,
    '2d4dd47b8501d12a21bcf25bdc536662fbcf3b140d2b67efaf28ea7c96515e68',
    'VALIDATED',
    1730,
    NULL,
    NOW()
FROM distilled_contract;

UPDATE global_skill
SET
    display_name = '中文网文写作基础契约',
    description = '由用户提供的 D:\\写作\\SKILL.md 熔炼而来，覆盖章节推进、人物、对白、节奏与去 AI 痕迹。',
    contract_json = (SELECT contract_json FROM global_skill_version WHERE id = '00000000-0000-0000-0000-000000000013'),
    current_version_id = '00000000-0000-0000-0000-000000000013',
    status = 'VALIDATED',
    version = version + 1,
    updated_at = NOW()
WHERE id = '00000000-0000-0000-0000-000000000011';

INSERT INTO global_skill_atomic_rule (id, skill_version_id, dimension, statement, rationale, evidence_refs, confidence, applicability, exclusions, created_at)
VALUES
    ('00000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-000000000013', 'NARRATIVE', '每场戏至少改变关系、信息、冲突、局势、能力、伏笔、误会或主角选择之一。', '防止日常空转，让章节具备可感知的信息增量。', '["D:\\写作\\SKILL.md#剧情推进规则"]'::jsonb, 0.98, '["续写", "章节创作", "大纲扩写"]'::jsonb, '["纯氛围片段需由用户明确要求"]'::jsonb, NOW()),
    ('00000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-000000000013', 'CHARACTER', '主角每章至少做一次主动判断、试探、隐瞒、反击或选择；情绪以可观察反应呈现。', '保持人物主动性，避免用旁白情绪标签替代行为。', '["D:\\写作\\SKILL.md#人物写作规则"]'::jsonb, 0.98, '["所有正文任务"]'::jsonb, '["用户明确指定被动视角实验时"]'::jsonb, NOW()),
    ('00000000-0000-0000-0000-000000000016', '00000000-0000-0000-0000-000000000013', 'EXPRESSION', '白描、具体动作、具体物件与自然口语优先；不同角色的句长、语气和停顿必须可区分。', '以细节承载情绪与设定，避免演讲式或同质化对话。', '["D:\\写作\\SKILL.md#对话规则", "D:\\写作\\SKILL.md#描写规则"]'::jsonb, 0.97, '["正文", "润色"]'::jsonb, '["用户明确要求诗性实验文风时"]'::jsonb, NOW()),
    ('00000000-0000-0000-0000-000000000017', '00000000-0000-0000-0000-000000000013', 'PACING', '新章节三段内抛出问题；冲突从异常到选择和代价逐步升级；结尾保留新钩子。', '建立连载阅读的进入速度、松紧节奏与下一章期待。', '["D:\\写作\\SKILL.md#开头规则", "D:\\写作\\SKILL.md#章节结构建议"]'::jsonb, 0.98, '["章节创作", "爆款开头"]'::jsonb, '["用户要求无钩子的收束章时"]'::jsonb, NOW()),
    ('00000000-0000-0000-0000-000000000018', '00000000-0000-0000-0000-000000000013', 'ANTI_PATTERN', '删除模板化对仗、假转折、意义膨胀、总结句收尾、解释性对话和连续同类 AI 痕迹。', '让正文以剧情、画面、动作和对话自然推进。', '["D:\\写作\\SKILL.md#AI痕迹规避规则", "D:\\写作\\SKILL.md#常见禁用句式"]'::jsonb, 0.99, '["正文", "润色"]'::jsonb, '["用户明确保留的原句"]'::jsonb, NOW()),
    ('00000000-0000-0000-0000-000000000019', '00000000-0000-0000-0000-000000000013', 'BOUNDARY', '用户已确认的正典、剧情、关系、字数、禁用句式和风格要求优先；缺少关键上下文时标注假设，不伪造事实。', 'Skill 是可复用约束，不是替代作者决定或覆盖正典的权力。', '["D:\\写作\\SKILL.md#使用方式", "D:\\写作\\SKILL.md#写作目标"]'::jsonb, 0.99, '["所有任务"]'::jsonb, '["未经用户确认的正典变更"]'::jsonb, NOW());
