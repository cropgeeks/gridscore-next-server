package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class ShareCodes
{
	private String ownerCode;
	private String editorCode;
	private String viewerCode;
}
