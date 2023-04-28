package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Measurement
{
	private String       timestamp;
	private List<String> values;
}
