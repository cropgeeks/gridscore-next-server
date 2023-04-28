package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Corners
{
	private LatLng topLeft;
	private LatLng topRight;
	private LatLng bottomRight;
	private LatLng bottomLeft;
}
