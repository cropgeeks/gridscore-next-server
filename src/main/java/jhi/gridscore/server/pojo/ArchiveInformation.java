package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class ArchiveInformation
{
	private String trialExportedOn;
	private String trialUpdatedOn;
	private Long fileSize;
}
