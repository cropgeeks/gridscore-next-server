package jhi.gridscore.server.pojo;

import jhi.gridscore.server.pojo.transaction.*;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.*;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Transaction
{
	private String                                trialId;
	private Map<String, List<PlotCommentContent>> plotCommentAddedTransactions;
	private Map<String, List<PlotCommentContent>> plotCommentDeletedTransactions;
	private Map<String, Boolean>                  plotMarkedTransactions;
	private Map<String, List<TraitMeasurement>>   plotTraitDataChangeTransactions;
	private List<TrialCommentContent>             trialCommentAddedTransactions;
	private List<TrialCommentContent>             trialCommentDeletedTransactions;
	private List<String>                          trialGermplasmAddedTransactions;
	private List<Trait>                           trialTraitAddedTransactions;
}
