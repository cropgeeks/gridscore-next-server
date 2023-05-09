package jhi.gridscore.server.pojo.transaction;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class PlotCommentContent extends PlotContent
{
	private String content;
	private String timestamp;
}
