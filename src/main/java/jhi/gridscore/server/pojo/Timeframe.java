package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Timeframe
{
	private String        start;
	private String        end;
	private TimeframeType type = TimeframeType.TRAIT_TIMEFRAME_TYPE_SUGGEST;

	public static enum TimeframeType
	{
		TRAIT_TIMEFRAME_TYPE_SUGGEST,
		TRAIT_TIMEFRAME_TYPE_ENFORCE
	}
}
