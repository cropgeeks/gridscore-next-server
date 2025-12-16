package jhi.gridscore.server.pojo;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@ToString
public class CellMetadata
{
	private String                         germplasm;
	private String                         barcode;
	private String                         friendlyName;
	private String                         pedigree;
	private String                         treatment;
	private String                         rep;

	public CellMetadata(Cell input)
	{
		this.germplasm = input.getGermplasm();
		this.barcode = input.getBarcode();
		this.friendlyName = input.getFriendlyName();
		this.pedigree = input.getPedigree();
		this.treatment = input.getTreatment();
		this.rep = input.getRep();
	}
}
