package cn.xmcraft.dreamport.server.questionnaire;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** 问卷题库三实体集中定义（dp_questionnaire / dp_question / dp_question_option） */
public final class QuestionnaireRecords {

    private QuestionnaireRecords() {
    }

    @Table("dp_questionnaire")
    public record Questionnaire(
            @Id Long id,
            String name,
            Boolean enabled,
            Integer passScore,
            Long createdAt
    ) {
        public Questionnaire {
            if (enabled == null) {
                enabled = true;
            }
            if (passScore == null) {
                passScore = 60;
            }
            if (createdAt == null) {
                createdAt = System.currentTimeMillis();
            }
        }
    }

    /** type: single_choice / multiple_choice / text */
    @Table("dp_question")
    public record Question(
            @Id Long id,
            Long questionnaireId,
            String questionZh,
            String questionEn,
            String type,
            Boolean required,
            Integer maxScore,
            String scoringRule,
            Boolean multiline,
            Integer minLength,
            Integer maxLength,
            Integer minSelections,
            Integer maxSelections,
            String placeholderZh,
            String placeholderEn,
            Integer sortOrder
    ) {
        public Question {
            if (required == null) {
                required = true;
            }
            if (maxScore == null) {
                maxScore = 0;
            }
            if (multiline == null) {
                multiline = false;
            }
            if (sortOrder == null) {
                sortOrder = 0;
            }
        }
    }

    @Table("dp_question_option")
    public record QuestionOption(
            @Id Long id,
            Long questionId,
            String textZh,
            String textEn,
            Integer score,
            Integer sortOrder
    ) {
        public QuestionOption {
            if (score == null) {
                score = 0;
            }
            if (sortOrder == null) {
                sortOrder = 0;
            }
        }
    }
}
