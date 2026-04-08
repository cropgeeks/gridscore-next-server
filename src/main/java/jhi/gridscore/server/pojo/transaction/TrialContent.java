package jhi.gridscore.server.pojo.transaction;

import jhi.gridscore.server.pojo.*;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.*;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class TrialContent
{
	private String               name;
	private String               description;
	private SocialShareConfig    socialShareConfig;
	private Markers              markers;
	private Corners              corners;
	private Map<String, Corners> plotCorners;
	private DimensionNames       dimensionNames;
}
