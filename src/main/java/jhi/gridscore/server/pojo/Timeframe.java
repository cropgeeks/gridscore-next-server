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
	private TimeframeType type = TimeframeType.SUGGEST;

	public static enum TimeframeType
	{
		SUGGEST,
		ENFORCE
	}
}
