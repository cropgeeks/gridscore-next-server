package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class Person
{
	private String     id;
	private String     name;
	private String     email;
	private PersonType type = PersonType.DATA_SUBMITTER;

	public static enum PersonType
	{
		DATA_COLLECTOR,
		DATA_SUBMITTER,
		AUTHOR,
		CORRESPONDING_AUTHOR,
		QUALITY_CHECKER
	}
}
