import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.*;


/*
IsoPhotContour_ by Gabriel Landini G.Landini at bham. ac. uk
This plugin creates a number of contour level curves equally separated in the
greyscale space
20 Oct 2003 Released 1.0
30 Nov 2003 version 1.1, changed equality test for strings.
3 Feb 2007 version 1.2 supports stacks, added 'none'
22 Feb 2010 editado por A. Perez Rozos para eliminar la limitacion
a imagenes de 8 bits y trabajar con imagenes de dosimetria de 32bits

*/
public class IsoDosisContour_ implements PlugIn {

	public void run(String arg) {

		ImagePlus img = WindowManager.getCurrentImage();
		if (img==null){
			IJ.error("Error","No image!.\nPlease open an image.");
			return;
		}

	/*	if (img.getType()!=ImagePlus.GRAY8){
			IJ.error("Error","8 bit images only!");
			return;
		}
  */
		ImageProcessor ip = img.getProcessor();
		int stk = img.getStackSize();

		int i, x, y, v, j=0, bck;
		int xe=ip.getWidth(), ye=ip.getHeight();
		double [] lev = {30,50,80,90,95,100,107};
		int [] col = new  int[8];
		double [][] p = new  double[xe][ye];
		boolean done=false, to8bit=false, contr=true, bb=false;
		String [] coverOption={"red","green","orange","yellow","cyan","blue","magenta"};
		String [] selcol = {"red","green","orange","yellow","cyan","blue","magenta"};
    double dprescrita=200.0;

		GenericDialog gd = new GenericDialog("IsoDosis");
		
		  gd.addNumericField ("Nivel de referencia 100%: (cGy o U.A.)",  200, 0);
					

			gd.addCheckbox ("Solamente contornos",contr);
			
      gd.addMessage("------------------------------------------------------------------");
	    gd.addMessage("A. Pérez Rozos. (Basado en los trabajos de G. Landini) Enero 2010");
	gd.addMessage("Hospital Virgen de las Nieves. Granada.");
	gd.addMessage("------------------------------------------------------------------");
	
	
			//gd.addCheckbox ("Fondo en negro",bb);
			//gd.addCheckbox ("Convertir a 8bit color", false);

			gd.showDialog();
			
			if (gd.wasCanceled()){
				done=true;
				return;
			}

			
				
				

			//	if (selcol[j].equals("red"))
					col[6]=((255 & 0xff)<<16)+((0&0xff)<<8)+(0&0xff);
			//	if (selcol[j].equals("blue"))
					col[1]=((0 & 0xff)<<16)+((0&0xff)<<8)+(255&0xff);
			//	if (selcol[j].equals("magenta"))
					col[0]=((255 & 0xff)<<16)+((0&0xff)<<8)+(255&0xff);
			//	if (selcol[j].equals("orange"))
					col[4]=((255 & 0xff)<<16)+((128&0xff)<<8)+(0&0xff);
			//	if (selcol[j].equals("green"))
					col[5]=((0 & 0xff)<<16)+((255&0xff)<<8)+(0&0xff);
			//	if (selcol[j].equals("cyan"))
					col[2]=((0 & 0xff)<<16)+((255&0xff)<<8)+(255&0xff);
			//	if (selcol[j].equals("yellow"))
					col[3]=((255 & 0xff)<<16)+((255&0xff)<<8)+(0&0xff);

			
      dprescrita=(double) gd.getNextNumber();
			contr= gd.getNextBoolean();
			//contr=true;
			//bb= gd.getNextBoolean();
			bb=false;
			//to8bit= gd.getNextBoolean();
      
      for(j=0;j<7;j++) { lev[j]=lev[j]*dprescrita/100;
      }


		if(bb==true)
			bck=0;
		else
			bck=((255 & 0xff)<<16)+((255 & 0xff)<<8)+(255 & 0xff);

/* Aqui duplica la imagen para tener un lugar donde volcar los datos */

IJ.run("Duplicate...", "title=Imagen Isodosis");
		IJ.run("RGB Color");
		//IJ.selectWindow("IsoPhot");
		ImagePlus img2 = WindowManager.getCurrentImage();
		ImageProcessor ip2 = img2.getProcessor();
		
		for (i=1; i<=stk; i++) {

			img.setSlice(i);

			for (y=0;y<ye; y++) {
				for (x=0; x<xe; x++) {
					p[x][y]=ip.getPixelValue(x,y);
				}
			}
			

			img2.setSlice(i);

			if (contr==true){
				for (y=0;y<ye; y++) {
					for (x=0; x<xe; x++)
						ip2.putPixel(x,y,bck);
				}
			}
			img.updateAndDraw();

			for (y=1;y<ye-1;y++) {
				for (x=1;x<xe-1;x++) {
					for (v=0;v<7;v++)
						if(p[x][y] <= lev[v] && (p[x-1][y-1] > lev[v] || p[x][y-1] > lev[v] || p[x+1][y-1] > lev[v]
						|| p[x-1][y]> lev[v] || p[x+1][y] > lev[v] || p[x-1][y+1]>lev[v] || p[x][y+1]>lev[v] || p[x+1][y+1]> lev[v])) {
						 ip2.putPixel(x,y,col[v]);
						 //ahora engordamos las lineas de isodosis
						 ip2.putPixel(x-1,y-1,col[v]);ip2.putPixel(x,y-1,col[v]);
						 ip2.putPixel(x,y+1,col[v]);ip2.putPixel(x+1,y,col[v]);}
						//ip2.putPixel(x,y,((255 & 0xff)<<16)+((0&0xff)<<8)+(0&0xff));
				}
			}
			// top, bottom, left & right borders
			y=0;
			for (x=1;x<xe-1;x++) {
				for (v=0;v<7;v++)
					if(p[x][y] <= lev[v] && (p[x-1][y]> lev[v] || p[x+1][y] > lev[v] ||
					 p[x-1][y+1]>lev[v] || p[x][y+1]>lev[v] || p[x+1][y+1]> lev[v]))
					ip2.putPixel(x,y,col[v]);
			}
			y=ye-1;
			for (x=1;x<xe-1;x++) {
				for (v=0;v<7;v++)
					if(p[x][y] <= lev[v] && (p[x-1][y-1] > lev[v] || p[x][y-1] > lev[v] ||
					p[x+1][y-1] > lev[v] || p[x-1][y]> lev[v] || p[x+1][y] > lev[v]))
						ip2.putPixel(x,y,col[v]);
			}
			x=0;
			for (y=1;y<ye-1;y++) {
				for (v=0;v<7;v++)
					if(p[x][y] <= lev[v] && (p[x][y-1] > lev[v] || p[x+1][y-1] > lev[v] ||
					p[x+1][y] > lev[v] || p[x][y+1]>lev[v] || p[x+1][y+1]> lev[v]))
						ip2.putPixel(x,y,col[v]);
			}
			x=xe-1;
			for (y=1;y<ye-1;y++) {
				for (v=0;v<7;v++)
					if(p[x][y] <= lev[v] && (p[x-1][y-1] > lev[v] || p[x][y-1] > lev[v] ||
					p[x-1][y]> lev[v] || p[x-1][y+1]>lev[v] || p[x][y+1]>lev[v]))
					ip2.putPixel(x,y,col[v]);
			}
			img2.updateAndDraw();
		}
		if (to8bit==true)
			IJ.run("8-bit Color", "number=256");

	}
}
