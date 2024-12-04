package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class UpdateStats
{
	private IndividualUpdates ownerUpdates  = new IndividualUpdates(0, 0, null);
	private IndividualUpdates editorUpdates = new IndividualUpdates(0, 0, null);
	private IndividualUpdates viewerUpdates = new IndividualUpdates(0, 0, null);

	@Getter
	@Setter
	@Accessors(chain = true)
	@NoArgsConstructor
	@AllArgsConstructor
	@ToString
	public static class IndividualUpdates
	{
		private Integer updateCount;
		private Integer loadCount;
		private String  lastUpdateOn;
	}
}
