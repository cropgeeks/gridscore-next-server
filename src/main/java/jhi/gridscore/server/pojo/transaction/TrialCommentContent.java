package jhi.gridscore.server.pojo.transaction;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class TrialCommentContent
{
	private String content;
	private String timestamp;
}
