package jhi.gridscore.server.pojo.transaction;

import jhi.gridscore.server.pojo.Event;
import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class TrialEventContent
{
	private String          timestamp;
	private String          content;
	private Event.EventType type;
	private Integer         impact;
}
