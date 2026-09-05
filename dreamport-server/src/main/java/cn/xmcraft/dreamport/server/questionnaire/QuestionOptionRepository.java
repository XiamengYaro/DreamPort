package cn.xmcraft.dreamport.server.questionnaire;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface QuestionOptionRepository extends CrudRepository<QuestionnaireRecords.QuestionOption, Long> {
    List<QuestionnaireRecords.QuestionOption> findByQuestionIdOrderBySortOrderAsc(Long questionId);
}
