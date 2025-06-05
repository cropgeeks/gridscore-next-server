package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class TrialStats
{
	private String createdOn;
	private String updatedOn;
	private Integer duration;
}
