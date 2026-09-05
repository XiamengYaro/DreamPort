package cn.xmcraft.dreamport.server.questionnaire;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface QuestionnaireRepository extends CrudRepository<QuestionnaireRecords.Questionnaire, Long> {
    List<QuestionnaireRecords.Questionnaire> findByEnabledTrue();
}
