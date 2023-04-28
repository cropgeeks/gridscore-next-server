package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Markers
{
	private Anchor anchor;
	private int    everyRow;
	private int    everyColumn;

	public static enum Anchor
	{
		topLeft,
		topRight,
		bottomRight,
		bottomLeft
	}
}
