package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Restrictions
{
	private Double       min;
	private Double       max;
	private List<String> categories;
}
