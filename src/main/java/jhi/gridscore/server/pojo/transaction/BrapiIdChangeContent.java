package jhi.gridscore.server.pojo.transaction;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.Map;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class BrapiIdChangeContent
{
	private Map<String, String> germplasmBrapiIds;
	private Map<String, String> traitBrapiIds;
}
