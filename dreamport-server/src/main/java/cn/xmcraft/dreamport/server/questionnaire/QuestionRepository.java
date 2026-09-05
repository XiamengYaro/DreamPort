package cn.xmcraft.dreamport.server.questionnaire;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface QuestionRepository extends CrudRepository<QuestionnaireRecords.Question, Long> {
    List<QuestionnaireRecords.Question> findByQuestionnaireIdOrderBySortOrderAsc(Long questionnaireId);
}
