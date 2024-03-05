package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Event
{
	private String    timestamp;
	private String    content;
	private EventType type;
	private Integer   impact;

	public static enum EventType
	{
		WEATHER,
		MANAGEMENT,
		OTHER
	}
}
