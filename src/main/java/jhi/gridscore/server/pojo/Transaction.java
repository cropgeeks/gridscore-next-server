package jhi.gridscore.server.pojo;

import com.google.gson.JsonElement;
import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Transaction
{
	private String               trialId;
	private TransactionOperation operation;
	private String               timestamp;
	private JsonElement          content;

	@Getter
	@ToString
	public static enum TransactionOperation
	{
		PLOT_COMMENT_ADDED,
		PLOT_COMMENT_DELETED,
		PLOT_MARKED_CHANGED,
		TRIAL_COMMENT_ADDED,
		TRIAL_COMMENT_DELETED,
		TRIAL_TRAITS_ADDED,
		TRAIT_DATA_CHANGED
	}
}
