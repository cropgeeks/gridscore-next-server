package jhi.gridscore.server.pojo.transaction;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class PlotDetailContent extends PlotContent
{
	private String pedigree;
	private String friendlyName;
	private String barcode;
	private String treatment;
}
