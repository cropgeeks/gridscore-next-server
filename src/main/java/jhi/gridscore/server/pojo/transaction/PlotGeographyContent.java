package jhi.gridscore.server.pojo.transaction;

import jhi.gridscore.server.pojo.LatLng;
import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class PlotGeographyContent extends PlotContent
{
	private LatLng center;
}
