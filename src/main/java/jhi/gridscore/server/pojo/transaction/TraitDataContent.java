package jhi.gridscore.server.pojo.transaction;

import jhi.gridscore.server.pojo.TraitMeasurement;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class TraitDataContent extends PlotContent
{
	private List<TraitMeasurement> measurements;
}
