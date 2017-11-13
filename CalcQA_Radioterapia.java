import ij.*;
import ij.plugin.*;
import ij.gui.*;
import ij.process.*;

/** This plugin does various calculations on two images or stacks.
Basado en Calculator Plus

Wayne Rasband (wsr at nih.gov) and
Gabriel Landini (G.Landini at bham.ac.uk) 

Adaptado en Enero 2009 por APerez Rozos, IJerez Sainz y Mlobato Munoz 
para tareas y calculos specificos de dosimetria con pelicula radiocromica
Adaptado en Enero 2010 por APerezRozos para pintar curvas de isodosis
*/ 

public class CalcQA_Radioterapia implements PlugIn {

    static String title = "CalcQA Radioterapia";
    static final int FCDTA=0, ANGGAMMA=1, SUBTRACT=2, DOSEDIFF=3, GAMMA=4, GAMMA2=5, ISODOS=6;
    static String[] ops = {"Distancia al acuerdo (DTA)", "Angulo fc.gamma: Atan(DTA/DosisTA)", "Dif. dosis abs.: i2 = (i1-i2)",
        "Dif. dosis (%): i2 = 100(1-i1/i2)", "Función Gamma, (dD %, dr mm)", "Gamma Rápida (dD %, dr mm)", "Isodosis"};
    static int operation = GAMMA2;
    static double k1 = 3.0;
    static int xh = 1;
    static String nombreresult="Result";
  static int yh = 1;
  static int yj=-14;
  static int xj=-14;
  static double DTA = 0;
  static double DosisTA = 0;
  static double fcgammaprov = 100;
  static double fcgamma = 100;
  static double anggamma = 0;
  static double dprescr=200;
  static double dumbralpc=30;
  static double dumbral=60;
    static double k2 = 3.0;
    static boolean createWindow = true;
    static boolean rgbPlanes = false;
    int[] wList;
    private String[] titles;
    int i1Index;
    int i2Index;
    ImagePlus i1;
    ImagePlus i2;
    boolean replicate;

    public void run(String arg) {
        if (IJ.versionLessThan("1.27w"))
            return;
        wList = WindowManager.getIDList();
        if (wList==null || wList.length<2) {
            IJ.showMessage(title, "Debes tener al menos dos imagenes de las mismas dimensiones abiertas");
            return;
        }
        titles = new String[wList.length];
        for (int i=0; i<wList.length; i++) {
            ImagePlus imp = WindowManager.getImage(wList[i]);
            if (imp!=null)
                titles[i] = imp.getTitle();
            else
                titles[i] = "";
        }
        
        if (!showDialog())
            return;
        
        long start = System.currentTimeMillis();
        boolean calibrated = i1.getCalibration().calibrated() || i2.getCalibration().calibrated();
        
       
        switch (operation) {


							case FCDTA:  
            nombreresult="DTA"; break;
              case ANGGAMMA:  
            nombreresult="AnguloGamma"; break;
            case SUBTRACT:  
             nombreresult="DifDosiscGy"; break;
            case DOSEDIFF:  
             nombreresult="DifDosis%"; break;
            case GAMMA:  
              nombreresult="Gamma"; break;
            case GAMMA2:  
             nombreresult="GammaFast"; break;
            case ISODOS:  
             nombreresult="Isodosis"; break;
            }
        
        
        
        
        
        if (calibrated)
            createWindow = true;
        if (createWindow) {
            if (replicate)
                i2 = replicateImage(i2, calibrated, i1.getStackSize());
            else
               i2 = duplicateImage(i2, calibrated);
            if (i2==null)
                {IJ.showMessage(title, "Out of memory"); return;}
            i2.show();
        } 
        calculate(i1, i2, k1, k2);
        IJ.showStatus(IJ.d2s((System.currentTimeMillis()-start)/1000.0, 2)+" seconds");
    }
    
    
    public boolean showDialog() {
        k1=3;
        k2=3;
        dumbralpc=30;
        dprescr=200;
        GenericDialog gd = new GenericDialog(title);
        gd.addChoice("i1: (Planificador)", titles, titles[0]);
        gd.addChoice("i2: (Referencia o Medida)", titles, titles[1]);
        gd.addChoice("Operador:", ops, ops[operation]);
        gd.addNumericField("dD: dDosis, %:", k1, 1);
        gd.addNumericField("dr: dDistancia, mm (máx 5mm):", k2, 1);
        gd.addNumericField("Dosis prescrita (cGy):", dprescr, 1);
        gd.addNumericField("Dosis umbral (% dosis prescrita):", dumbralpc, 1);
        //gd.addCheckbox("RGB operations", rgbPlanes);
        gd.addCheckbox("Crear en una nueva imagen", createWindow);
        gd.addMessage("-------------------------------------------------------------------------------------------");
	gd.addMessage("A. Pérez Rozos, M. Lobato Muñoz, I. Jerez Sainz");
	gd.addMessage("Servicio de radiofísica y Protección Radiológica. Hospital Virgen de la Victoria");
	gd.addMessage("Campus de Teatinos s/n. 29010 Málaga (Spain)");
	gd.addMessage("aprozos@gmail.com");
	gd.addMessage("                                                                         v.10.01 Enero 2010");
	gd.addMessage("-------------------------------------------------------------------------------------------");
	gd.showDialog();
        if (gd.wasCanceled())
            return false;
        int i1Index = gd.getNextChoiceIndex();
        int i2Index = gd.getNextChoiceIndex();
        operation = gd.getNextChoiceIndex();
        k1 = gd.getNextNumber();
        k2 = 2.83465*gd.getNextNumber(); /** se multiplica por 2.83465 para pasar a píxeles (72 ppp) */
        dprescr = gd.getNextNumber();
        dumbralpc = gd.getNextNumber()/100;
        
        if(dumbralpc>1) dumbralpc=0.3;
        if(dumbralpc<0) dumbralpc=0.3;
        dumbral=dprescr*dumbralpc;
        
	if (k2>15) k2 = 14;
        //rgbPlanes = gd.getNextBoolean();
        createWindow = gd.getNextBoolean();
        i1 = WindowManager.getImage(wList[i1Index]);
        i2 = WindowManager.getImage(wList[i2Index]);
	if (i1.getBitDepth()==24 && i2.getBitDepth()==24)
		rgbPlanes = true;
        int d1 = i1.getStackSize();
        int d2 = i2.getStackSize();
        if (d2==1 && d1>1) {
            createWindow = true;
            replicate = true;
        }
        return true;
    }

    public void calculate(ImagePlus i1, ImagePlus i2, double k1, double k2) {
        double v1, v2=0, r1, g1, b1, r2, g2, b2;
		int iv1, iv2, r, g=0, b=0; 
        int width  = i1.getWidth();
        int height = i1.getHeight();
        ImageProcessor ip1, ip2;
        int slices1 = i1.getStackSize();
        int slices2 = i2.getStackSize();
        float[] ctable1 = i1.getCalibration().getCTable();
        float[] ctable2 = i2.getCalibration().getCTable();
        ImageStack stack1 = i1.getStack();
        ImageStack stack2 = i2.getStack();
        int currentSlice = i2.getCurrentSlice();

        for (int n=1; n<=slices2; n++) {
            ip1 = stack1.getProcessor(n<=slices1?n:slices1);
            ip2 = stack2.getProcessor(n);
            ip1.setCalibrationTable(ctable1);
            ip2.setCalibrationTable(ctable2);

/** Rellenamos con 0 llos bordes de la imagen */
        for (int xfill=0;xfill<width;xfill++) {
          for (int yfill=0;yfill<14;yfill++) {
          ip2.putPixelValue(xfill,yfill,0);
          ip2.putPixelValue(xfill,height-yfill-1,0);
                  }
        }
        for (int xfill=0;xfill<height;xfill++) {
          for (int yfill=0;yfill<14;yfill++) {
          ip2.putPixelValue(yfill,xfill,0);
          ip2.putPixelValue(width-yfill-1,xfill,0);
                  }
        }
        
				for (int x=14; x<width-14; x++) {
				      IJ.showProgress(x/width);
					for (int y=14; y<height-14; y++) {
						fcgamma=100.0;
						v1 = ip1.getPixelValue(x,y);
						v2 = ip2.getPixelValue(x,y);
						switch (operation) {


							case FCDTA: 

/** Aqui empieza el bucle definido por HVV para calcular la distancia al acuerdo (tolerancia de igualdad dDosis) */
/** De momento estamos evaluando 14 píxeles alrededor del punto en consideración (gamma 5mm/5%) */
yj=-14;
while (yj<14 && fcgamma>1) {

  yh = y + yj;
  for (int xj=-14; xj<14; xj++) {
    xh = x + xj; 
    v1 = ip1.getPixelValue(xh,yh);
    DTA=(xj*xj+yj*yj)/(k2*k2);
    if (v2==0.0)
	    DosisTA = 0.0;
	  else
	    DosisTA=(v2-v1)*(v2-v1)/(k1*k1*v2*v2/10000);
    if (DosisTA < 1) {
	DosisTA=0.0; /** Con esto pesamos igual todos los puntos que coinciden en la tolerancia al acuerdo */
	fcgammaprov = Math.sqrt(DTA);
	if(fcgammaprov<fcgamma) { 
	  fcgamma = fcgammaprov;
	/**  anggamma = Math.sqrt(DTA)*k2/2.83465; /** Se multiplica por ese número (72 ppp) para pasarlo a mm */
		   }
}

}
yj=yj+1;
}
	    if (fcgamma<1) {v2=0;} else
	    {v2=fcgamma*k2/2.83465;} 
	    
 break;



							case ANGGAMMA: 
/** Aqui empieza el bucle definido por HVV para calcular la tangente de la función gamma */

/** De momento estamos evaluando 14 píxeles alrededor del punto en consideración (gamma 5mm/5%) */
if (v2<dumbral) {
                v2=0;
                break;}

for (int yj=-14; yj<15; yj++) {
  yh = y + yj;
  for (int xj=-14; xj<15; xj++) {
    xh = x + xj; 
    v1 = ip1.getPixelValue(xh,yh);
    DTA=(xj*xj+yj*yj)/(k2*k2);
    if (v2==0.0)
	    DosisTA = 0.0;
	  else
	    DosisTA=(v2-v1)*(v2-v1)/(k1*k1*v2*v2/10000);
    fcgammaprov = Math.sqrt(DTA+DosisTA);
  
    if(fcgammaprov<fcgamma) { 
	fcgamma = fcgammaprov;
	if(DosisTA==0.0) 
	    anggamma = 0.0;
	  else
	  anggamma=Math.atan(Math.sqrt(DTA/DosisTA));
	  
			    }
}
}

	    v2=anggamma;
			    break;


							case SUBTRACT: 
							
										v2 -= v1; break;


							case DOSEDIFF: 
							
							  		v2 = v2!=0.0?(100*(1-v1/v2)):0.0; break;


							case GAMMA: 
/** Aqui empieza el bucle definido por HVV para calcular la función gamma */


/** De momento estamos evaluando 14 píxeles alrededor del punto en consideración (gamma 5mm/5%) */
if (v2<dumbral) {
                v2=-1;
                break;}

for (int yj=-14; yj<15; yj++) {
  yh = y + yj;
  for (int xj=-14; xj<15; xj++) {
    xh = x + xj; 
    v1 = ip1.getPixelValue(xh,yh);
    DTA=(xj*xj+yj*yj)/(k2*k2);
    if (v2==0.0)
	    DosisTA = 0.0;
	  else
	    DosisTA=(v2-v1)*(v2-v1)/(k1*k1*v2*v2/10000);
    fcgammaprov = Math.sqrt(DTA+DosisTA);
    if(fcgammaprov<fcgamma) 
	fcgamma = fcgammaprov; 
}
}
	    v2=fcgamma;
 break;
 
 							case GAMMA2: 
/** Cálculo rápido de la fc gamma */
int yj=0;
int xj=0;

if (v2<dumbral) {
                v2=-1;
                break;}
                
v1 = ip1.getPixelValue(x,y);
if (v2==0.0)
	    DosisTA = 0.0;
	  else
	    DosisTA=(v2-v1)*(v2-v1)/(k1*k1*v2*v2/10000);
    
    if(DosisTA<1) { 
    v2=Math.sqrt(DosisTA);
	break;}

yj=-1;

while (yj<14 && fcgamma>1)  {
yj=yj+1;
xj=-yj-1;  

while (xj<yj && fcgamma>1)  {
    xj=xj+1;
    xh = x + xj; 
    v1 = ip1.getPixelValue(xh,y+yj);
   
   
    DTA=(xj*xj+yj*yj)/(k2*k2);
   
   
    if (v2==0.0)
	    DosisTA = 0.0;
	  else
	    DosisTA=(v2-v1)*(v2-v1)/(k1*k1*v2*v2/10000);
    fcgammaprov = Math.sqrt(DTA+DosisTA);
    if(fcgammaprov<fcgamma) 
	fcgamma = fcgammaprov; 
	 
    v1 = ip1.getPixelValue(xh,y-yj);
    if (v2==0.0)
	    DosisTA = 0.0;
	  else
	    DosisTA=(v2-v1)*(v2-v1)/(k1*k1*v2*v2/10000);
    fcgammaprov = Math.sqrt(DTA+DosisTA);
    if(fcgammaprov<fcgamma) 
	fcgamma = fcgammaprov; 
	
	  v1 = ip1.getPixelValue(y-yj,xh);
    if (v2==0.0)
	    DosisTA = 0.0;
	  else
	    DosisTA=(v2-v1)*(v2-v1)/(k1*k1*v2*v2/10000);
    fcgammaprov = Math.sqrt(DTA+DosisTA);
    if(fcgammaprov<fcgamma) 
	fcgamma = fcgammaprov; 
	
	  v1 = ip1.getPixelValue(y+yj,xh);
    if (v2==0.0)
	    DosisTA = 0.0;
	  else
	    DosisTA=(v2-v1)*(v2-v1)/(k1*k1*v2*v2/10000);
    fcgammaprov = Math.sqrt(DTA+DosisTA);
    if(fcgammaprov<fcgamma) 
	fcgamma = fcgammaprov; 
 
	           
}

}

              v2=fcgamma;
              break;
/** Aquí termina el bucle, debe devolverse el valor de la función gamma para el punto evaluado */
            case ISODOS:
          
          
            /*v2=x;*/ break;
						
						}
						/** v2 = v2*k1 + k2; */ 
						/** v2=fcgamma; */


						ip2.putPixelValue(x, y, v2);
					}  
				}
			
			
				
            if (n==currentSlice) {
                i2.getProcessor().resetMinAndMax();
                i2.updateAndDraw();
            }    
            
            switch (operation) {


							case FCDTA:  
            IJ.setMinAndMax(0.000000000, 3.000000000); break;
              case ANGGAMMA:  
            IJ.setMinAndMax(0.000000000, 2.000000000); break;
            case SUBTRACT:  
            IJ.setMinAndMax(-20.0000000, 20.00000000); break;
            case DOSEDIFF:  
            IJ.setMinAndMax(-10.0000000, 10.00000000); break;
            case GAMMA:  
            IJ.setMinAndMax(0.000000000, 2.000000000); break;
            case GAMMA2:  
            IJ.setMinAndMax(0.000000000, 2.000000000); break;
            case ISODOS:
            
   /*         		ImagePlus img = WindowManager.getCurrentImage();
		if (img==null){
			IJ.error("Error","No image!.\nPlease open an image.");
			return;
		}

	
  
		ImageProcessor ip = img.getProcessor(); */
		int stk = i1.getStackSize();

		int i, x, y, v, j=0, bck;
		int xe=width;
		int ye=height;
		double [] lev = {dumbralpc*100,50,80,90,95,100,107};
		int [] col = new  int[8];
		double [][] p = new  double[xe][ye];
		double [][] p2 = new  double[xe][ye];
		boolean done=false, to8bit=false, contr=true, bb=false;
		String [] coverOption={"red","green","orange","yellow","cyan","blue","magenta"};
		String [] selcol = {"red","green","orange","yellow","cyan","blue","magenta"};
    
    double dprescrita=dprescr;

	GenericDialog gd = new GenericDialog(title);
        
  
				
				

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

			
      // dprescrita=(double) gd.getNextNumber();
			// contr= gd.getNextBoolean();
			contr=true;
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

/*    IJ.run("Duplicate...", "title=IsoPhot"); */
 		IJ.selectWindow("Isodosis");
 		IJ.run("RGB Color");
		
		
		ImagePlus i3 = WindowManager.getCurrentImage();
		ImageProcessor ip3 = i3.getProcessor(); 
		
		for (i=1; i<=stk; i++) {

			i1.setSlice(i);

			for (y=0;y<ye; y++) {
				for (x=0; x<xe; x++) {
					p[x][y]=ip2.getPixelValue(x,y);
					p2[x][y]=ip1.getPixelValue(x,y);
				}
			}
/*			
  gd.addMessage("-------------------------------------------------------------------------------------------");
	gd.addMessage("200,200" + p[200][200]);
	gd.addMessage("200,400" + p[200][400]);
	gd.addMessage("stack size" + stk);
	gd.addMessage("alberto.perez.sspa@juntadeandalucia.es");
	gd.addMessage("                                                                          v.0901 Enero 2009");
	gd.addMessage("-------------------------------------------------------------------------------------------");
	gd.showDialog();
*/	
			i3.setSlice(i);

			if (contr==true){
				for (y=0;y<ye; y++) {
					for (x=0; x<xe; x++)
						ip3.putPixel(x,y,bck);
				}
			}
			i1.updateAndDraw();

			for (y=1;y<ye-1;y++) {
				for (x=1;x<xe-1;x++) {
				// Si se quiere que aparezca la imagen se descomenta la linea
         /*   ip3.putPixelValue(x,y,p[x][y]); */
					for (v=0;v<7;v++) {
						if(p[x][y] <= lev[v] && (p[x-1][y-1] > lev[v] || p[x][y-1] > lev[v] || p[x+1][y-1] > lev[v]
						|| p[x-1][y]> lev[v] || p[x+1][y] > lev[v] || p[x-1][y+1]>lev[v] || p[x][y+1]>lev[v] || p[x+1][y+1]> lev[v])) {
						 ip3.putPixel(x,y,col[v]);
						 //ahora engordamos las lineas de isodosis
						 ip3.putPixel(x-1,y-1,col[v]);ip3.putPixel(x,y-1,col[v]);
						 ip3.putPixel(x,y+1,col[v]);ip3.putPixel(x+1,y,col[v]);}
						if(p2[x][y] <= lev[v] && (p2[x-1][y-1] > lev[v] || p2[x][y-1] > lev[v] || p2[x+1][y-1] > lev[v]
						|| p2[x-1][y]> lev[v] || p2[x+1][y] > lev[v] || p2[x-1][y+1]>lev[v] || p2[x][y+1]>lev[v] || p2[x+1][y+1]> lev[v])) {
						 ip3.putPixel(x,y,col[v]);} 
						 //ahora engordamos las lineas de isodosis
						 //ip3.putPixel(x-1,y-1,col[v]);ip3.putPixel(x,y-1,col[v]);
						 //ip3.putPixel(x,y+1,col[v]);ip3.putPixel(x+1,y,col[v]);}
						 }
				}
			}
			// top, bottom, left & right borders
			y=0;
			for (x=1;x<xe-1;x++) {
				for (v=0;v<7;v++)
					if(p[x][y] <= lev[v] && (p[x-1][y]> lev[v] || p[x+1][y] > lev[v] ||
					 p[x-1][y+1]>lev[v] || p[x][y+1]>lev[v] || p[x+1][y+1]> lev[v]))
					ip3.putPixel(x,y,col[v]);
			}
			y=ye-1;
			for (x=1;x<xe-1;x++) {
				for (v=0;v<7;v++)
					if(p[x][y] <= lev[v] && (p[x-1][y-1] > lev[v] || p[x][y-1] > lev[v] ||
					p[x+1][y-1] > lev[v] || p[x-1][y]> lev[v] || p[x+1][y] > lev[v]))
						ip3.putPixel(x,y,col[v]);
			}
			x=0;
			for (y=1;y<ye-1;y++) {
				for (v=0;v<7;v++)
					if(p[x][y] <= lev[v] && (p[x][y-1] > lev[v] || p[x+1][y-1] > lev[v] ||
					p[x+1][y] > lev[v] || p[x][y+1]>lev[v] || p[x+1][y+1]> lev[v]))
						ip3.putPixel(x,y,col[v]);
			}
			x=xe-1;
			for (y=1;y<ye-1;y++) {
				for (v=0;v<7;v++)
					if(p[x][y] <= lev[v] && (p[x-1][y-1] > lev[v] || p[x][y-1] > lev[v] ||
					p[x-1][y]> lev[v] || p[x-1][y+1]>lev[v] || p[x][y+1]>lev[v]))
					ip3.putPixel(x,y,col[v]);
			}
			i3.updateAndDraw();
		}
		if (to8bit==true)
			IJ.run("8-bit Color", "number=256");
			break;
            
            
            
            }
           
          /**  IJ.showProgress((double)n/slices2); */
            IJ.showStatus(n+"/"+slices2);
        }
    }

   ImagePlus duplicateImage(ImagePlus img1, boolean calibrated) {
        ImageStack stack1 = img1.getStack();
        int width = stack1.getWidth();
        int height = stack1.getHeight();
        int n = stack1.getSize();
        ImageStack stack2 = img1.createEmptyStack();
        float[] ctable = img1.getCalibration().getCTable();
        try {
            for (int i=1; i<=n; i++) {
                ImageProcessor ip1 = stack1.getProcessor(i);
                ImageProcessor ip2 = ip1.duplicate(); 
                if (calibrated) {
                    ip2.setCalibrationTable(ctable);
                    ip2 = ip2.convertToFloat();
                }
                stack2.addSlice(stack1.getSliceLabel(i), ip2);
            }
        }
        catch(OutOfMemoryError e) {
            stack2.trim();
            stack2 = null;
            return null;
        }
        ImagePlus img2 =  new ImagePlus(nombreresult, stack2);
        return img2;
    }

  ImagePlus replicateImage(ImagePlus img1, boolean calibrated, int n) {
        ImageProcessor ip1 = img1.getProcessor();
        int width = ip1.getWidth();
        int height = ip1.getHeight();
        ImageStack stack2 = img1.createEmptyStack();
        float[] ctable = img1.getCalibration().getCTable();
        try {
            for (int i=1; i<=n; i++) {
                ImageProcessor ip2 = ip1.duplicate(); 
                if (calibrated) {
                    ip2.setCalibrationTable(ctable);
                    ip2 = ip2.convertToFloat();
                }
                stack2.addSlice(null, ip2);
            }
        }
        catch(OutOfMemoryError e) {
            stack2.trim();
            stack2 = null;
            return null;
        }
        ImagePlus img2 =  new ImagePlus(nombreresult, stack2);
        return img2;
    }

} 

