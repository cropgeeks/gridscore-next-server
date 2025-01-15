package jhi.gridscore.server.pojo.transaction;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class TraitEditContent
{
	private String  id;
	private String  name;
	private String  description;
	private String  group;
	private Boolean hasImage;
	private String  imageUrl;
	private String  timestamp;
}
