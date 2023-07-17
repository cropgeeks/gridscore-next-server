package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class LatLng
{
	private Double lat;
	private Double lng;

	public boolean isValid()
	{
		return lat != null && lng != null;
	}
}
