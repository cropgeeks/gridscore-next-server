package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Layout
{
	private int     rows;
	private int     columns;
	private Corners corners;
	private Markers markers;
}
