/*
 *  $RCSfile$
 *  $Author: egonw $
 *  $Date: 2007-01-04 17:26:00 +0000 (Thu, 04 Jan 2007) $
 *  $Revision: 7634 $
 *
 *  Copyright (C) 1997-2008 Egon Willighagen, Stefan Kuhn
 *
 *  Contact: cdk-jchempaint@lists.sourceforge.net
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2.1
 *  of the License, or (at your option) any later version.
 *  All we ask is that proper credit is given for our work, which includes
 *  - but is not limited to - adding the above copyright notice to the beginning
 *  of your source code files, and to any copyright notice that you may distribute
 *  with programs based on this work.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.openscience.jchempaint.action;

import org.openscience.cdk.ChemFile;
import org.openscience.cdk.ChemModel;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.depict.DepictionGenerator;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.geometry.GeometryUtil;
import org.openscience.cdk.graph.ConnectivityChecker;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomContainerSet;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemFile;
import org.openscience.cdk.interfaces.IChemModel;
import org.openscience.cdk.interfaces.IChemObject;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.interfaces.IReaction;
import org.openscience.cdk.io.CMLReader;
import org.openscience.cdk.io.IChemObjectWriter;
import org.openscience.cdk.io.ISimpleChemObjectReader;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.io.MDLV2000Writer;
import org.openscience.cdk.io.RGroupQueryReader;
import org.openscience.cdk.io.ReaderFactory;
import org.openscience.cdk.isomorphism.matchers.IRGroupQuery;
import org.openscience.cdk.isomorphism.matchers.RGroupQuery;
import org.openscience.cdk.layout.StructureDiagramGenerator;
import org.openscience.cdk.renderer.selection.IChemObjectSelection;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.CDKHydrogenAdder;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.manipulator.ChemFileManipulator;
import org.openscience.cdk.tools.manipulator.ChemModelManipulator;
import org.openscience.cdk.tools.manipulator.MoleculeSetManipulator;
import org.openscience.cdk.tools.manipulator.ReactionManipulator;
import org.openscience.jchempaint.AtomBondSet;
import org.openscience.jchempaint.GT;
import org.openscience.jchempaint.JChemPaintPanel;
import org.openscience.jchempaint.application.JChemPaint;
import org.openscience.jchempaint.controller.ControllerHub;
import org.openscience.jchempaint.controller.MoveModule;
import org.openscience.jchempaint.controller.RemoveModule;
import org.openscience.jchempaint.controller.SelectSquareModule;
import org.openscience.jchempaint.dialog.TemplateBrowser;
import org.openscience.jchempaint.inchi.InChITool;
import org.openscience.jchempaint.renderer.JChemPaintRendererModel;
import org.openscience.jchempaint.renderer.Renderer;
import org.openscience.jchempaint.renderer.selection.LogicalSelection;
import org.openscience.jchempaint.renderer.selection.RectangleSelection;
import org.openscience.jchempaint.renderer.selection.ShapeSelection;
import org.openscience.jchempaint.renderer.selection.SingleSelection;
import org.openscience.jchempaint.rgroups.RGroupHandler;

import javax.swing.JOptionPane;
import javax.vecmath.Point2d;
import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;

/**
 * Action to copy/paste structures.
 */
public class CopyPasteAction extends JCPAction {

    private static final long serialVersionUID = -3343207264261279526L;

    private DataFlavor molFlavor = new DataFlavor(
            "chemical/x-mdl-molfile", "mdl mol file format");
    private DataFlavor svgFlavor = new DataFlavor(
            "image/svg+xml",          "scalable vector graphics");
    private DataFlavor cmlFlavor = new DataFlavor(
            "image/cml",          "chemical markup language");
    private DataFlavor smilesFlavor = new DataFlavor(
            "chemical/x-daylight-smiles", "smiles format");


    private void addToClipboard(Clipboard clipboard, IAtomContainer container) {
        try {
        	for (IBond bond: container.bonds())
        		if (bond.getAtomCount() < 2 
        				|| !container.contains(bond.getAtom(0))
        				|| !container.contains(bond.getAtom(1)))
        			container.removeBond(bond);
        	if (container.getAtomCount() > 0) {
        		JcpSelection jcpselection = new JcpSelection((IAtomContainer)container.clone());
        		clipboard.setContents(jcpselection,null);
        	}
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
    }

    private boolean supported(Transferable transfer, DataFlavor flavor) {
        return transfer != null && transfer.isDataFlavorSupported(flavor);
    }

    public void actionPerformed(ActionEvent e) {
        logger.info("  type  ", type);
        logger.debug("  source ", e.getSource());
        
        JChemPaintRendererModel renderModel = jcpPanel.get2DHub().getRenderer().getRenderer2DModel();
        IChemModel chemModel = jcpPanel.getChemModel();
        Clipboard sysClip = jcpPanel.getToolkit().getSystemClipboard();

        if ("copy".equals(type)) {
            handleSystemClipboard(sysClip);
            IAtom atomInRange = null;
            IChemObject object = getSource(e);
            logger.debug("Source of call: ", object);
            if (object instanceof IAtom) {
                atomInRange = (IAtom) object;
            } else {
                atomInRange = renderModel.getHighlightedAtom();
            }
            if (atomInRange != null) {
                IAtomContainer tocopyclone =
                    atomInRange.getBuilder().newInstance(IAtomContainer.class);
                try {
					tocopyclone.addAtom((IAtom) atomInRange.clone());
				} catch (CloneNotSupportedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
                addToClipboard(sysClip, tocopyclone);
            }
            else if(renderModel.getHighlightedBond()!=null){
                IBond bond = renderModel.getHighlightedBond();
                if (bond != null) {
                    IAtomContainer tocopyclone =
                        bond.getBuilder().newInstance(IAtomContainer.class);
                    try {
                        tocopyclone.addAtom((IAtom) bond.getAtom(0).clone());
                        tocopyclone.addAtom((IAtom) bond.getAtom(1).clone());
                    } catch (CloneNotSupportedException e1) {
                        e1.printStackTrace();
                    }
                    tocopyclone.addBond(bond.getBuilder().newInstance(IBond.class,tocopyclone.getAtom(0), tocopyclone.getAtom(1), bond.getOrder()));
                    addToClipboard(sysClip, tocopyclone);
                }
            }else if(renderModel.getSelection().getConnectedAtomContainer()!=null){
            	addToClipboard(sysClip,
					        renderModel.getSelection().getConnectedAtomContainer());
            }else{
				addToClipboard(sysClip, JChemPaintPanel.getAllAtomContainersInOne(chemModel));
            }
        } else if ("copyAsSmiles".equals(type)) {
            handleSystemClipboard(sysClip);
            try {
                
                final IAtomContainer selection = renderModel.getSelection().getConnectedAtomContainer();
                
                if(selection!=null){
                    final IChemObjectBuilder bldr = selection.getBuilder();
                    IChemModel selectionModel = bldr.newInstance(IChemModel.class);
                    selectionModel.setMoleculeSet(bldr.newInstance(IAtomContainerSet.class));
                    selectionModel.getMoleculeSet().addAtomContainer(selection);
                    sysClip.setContents(new SmilesSelection(CreateSmilesAction.getSmiles(selectionModel)), null);
                }else{
                    sysClip.setContents(new SmilesSelection(CreateSmilesAction.getSmiles(chemModel)),null);
                }
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        } else if ("eraser".equals(type)) {
            jcpPanel.get2DHub().clearPhantoms();
            RemoveModule newActiveModule = new RemoveModule(jcpPanel.get2DHub());
            newActiveModule.setID(type);
            jcpPanel.get2DHub().setActiveDrawModule(newActiveModule);
            IAtom atomInRange = null;
            IBond bondInRange = null;
            IChemObject object = getSource(e);
            logger.debug("Source of call: ", object);
            if (object instanceof IAtom) {
                atomInRange = (IAtom) object;
            } else {
                atomInRange = renderModel.getHighlightedAtom();
            }
            if (object instanceof IBond) {
                bondInRange = (IBond) object;
            } else {
                bondInRange = renderModel.getHighlightedBond();
            }

            IAtom newHighlightAtom = null;

            if (renderModel.getSelection() != null && renderModel.getSelection().isFilled()) {

                IChemObjectSelection selection = renderModel.getSelection();
                AtomBondSet atomBondSet = new AtomBondSet();
                for (IAtom atom : selection.elements(IAtom.class)) {
                    atomBondSet.add(atom);

                    // set the hot spot to the attached atom with the highest index
                    for (IBond bond : atom.bonds()) {
                        IAtom nbor = bond.getOther(atom);
                        if (!selection.contains(nbor) &&
                            (newHighlightAtom == null || nbor.getIndex() > newHighlightAtom.getIndex()))
                            newHighlightAtom = nbor;
                    }
                }
                for (IBond bond : selection.elements(IBond.class))
                    atomBondSet.add(bond);
                jcpPanel.get2DHub().deleteFragment(atomBondSet);
                renderModel.setSelection(new LogicalSelection(LogicalSelection.Type.NONE));
                jcpPanel.get2DHub().updateView();
            } else if (atomInRange != null) {

                if (atomInRange.equals(renderModel.getHighlightedAtom())) {
                    IAtomContainer container = ChemModelManipulator.getRelevantAtomContainer(jcpPanel.getChemModel(), atomInRange);
                    if (container != null) {
                        for (IBond bond : container.getConnectedBondsList(atomInRange))
                            newHighlightAtom = bond.getOther(atomInRange);
                    }
                }

                jcpPanel.get2DHub().removeAtom(atomInRange);
            } else if (bondInRange != null) {

                IAtomContainer container = ChemModelManipulator.getRelevantAtomContainer(jcpPanel.getChemModel(), bondInRange);
                if (container != null) {
                    for (IBond bond : container.getConnectedBondsList(bondInRange.getBegin()))
                        newHighlightAtom = bond.getOther(bondInRange.getBegin());
                    for (IBond bond : container.getConnectedBondsList(bondInRange.getEnd()))
                        newHighlightAtom = bond.getOther(bondInRange.getEnd());
                }

                jcpPanel.get2DHub().removeBond(bondInRange);
            }

            // no new hotspot? go to last atom added
            if (newHighlightAtom == null) {
                for (IAtomContainer ac : jcpPanel.getChemModel().getMoleculeSet()) {
                    if (!ac.isEmpty())
                        newHighlightAtom = ac.getAtom(ac.getAtomCount()-1);
                }
            }

            jcpPanel.get2DHub().getRenderer().getRenderer2DModel().setHighlightedAtom(newHighlightAtom);
            jcpPanel.get2DHub().getRenderer().getRenderer2DModel().setHighlightedBond(null);

        } else if (type.indexOf("pasteTemplate")>-1){
            //if templates are shown, we extract the tab to show if any
            String templatetab="";
            if(type.indexOf("_")>-1){
                templatetab=type.substring(type.indexOf("_")+1);
            }
            TemplateBrowser templateBrowser = new TemplateBrowser(templatetab);
            if(templateBrowser.getChosenmolecule()!=null){
                scaleStructure(templateBrowser.getChosenmolecule());
                insertStructure(templateBrowser.getChosenmolecule(), renderModel);
            }
        } else if ("paste".equals(type)) {
            handleSystemClipboard(sysClip);
            Transferable transfer = sysClip.getContents( null );
            ISimpleChemObjectReader reader = null;
            String content=null;
            
            if (supported(transfer, molFlavor) ) {
                StringBuffer sb = new StringBuffer();
            	try {
                    //StringBufferInputStream sbis=null;
					//sbis = (StringBufferInputStream) transfer.getTransferData(molFlavor);

                    StringReader sbis=null;
					sbis = (StringReader) transfer.getTransferData(molFlavor);

            		
            		int x;
                    while((x=sbis.read())!=-1){
                        sb.append((char)x);
                    }
                    reader = new MDLV2000Reader(new StringReader(sb.toString()));
				} catch (UnsupportedFlavorException e1) {
					e1.printStackTrace();
				} catch (IOException e1) {
					e1.printStackTrace();
            	} catch (Exception e1) {
                	reader = new RGroupQueryReader(new StringReader(sb.toString()));
                }

            } else if (supported(transfer, DataFlavor.stringFlavor) ) {
                try {
                    content = (String) transfer.getTransferData(DataFlavor.stringFlavor);
                    reader = new ReaderFactory().createReader(new StringReader(content));
                    //System.out.println(reader.getClass());
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }

            // if looks like CML - InputStream required. Reader throws error.
            if(content!=null && content.indexOf("cml")>-1) {
                reader = new CMLReader(new ByteArrayInputStream(content.getBytes()));
            }

            IAtomContainer toPaste = null;
            boolean rgrpQuery=false;
            if (reader != null) {
                IAtomContainer readMolecule =
                    chemModel.getBuilder().newInstance(IAtomContainer.class);
                try {
                    if (reader.accepts(IAtomContainer.class)) {
                        toPaste = (IAtomContainer) reader.read(readMolecule);
                    } else if (reader.accepts(ChemFile.class)) {
                        toPaste = readMolecule;
                        IChemFile file = (IChemFile) reader.read(new ChemFile());
                        for (IAtomContainer ac :
                            ChemFileManipulator.getAllAtomContainers(file)) {
                            toPaste.add(ac);

                        }
                	} else if (reader.accepts(RGroupQuery.class)) {
	        			rgrpQuery=true;
                		IRGroupQuery rgroupQuery = (RGroupQuery) reader.read(new RGroupQuery(DefaultChemObjectBuilder.getInstance()));
	        			chemModel = new ChemModel();
	        			RGroupHandler rgHandler =  new RGroupHandler(rgroupQuery, this.jcpPanel);
	        			this.jcpPanel.get2DHub().setRGroupHandler(rgHandler);
	        			chemModel.setMoleculeSet(rgHandler.getMoleculeSet(chemModel));
	        			rgHandler.layoutRgroup();
	        			
                	}

                } catch (CDKException e1) {
                    e1.printStackTrace();
                }
            }

            //Attempt SMILES or InChI if no reader is found for content.
            if(rgrpQuery!=true && toPaste == null &&
                    supported(transfer, DataFlavor.stringFlavor)) {
                try{
                    if (content.toLowerCase().indexOf("inchi")>-1 ) { 
                        toPaste = InChITool.parseInChI(content);
                    }
                    else {    
                        SmilesParser sp = new SmilesParser(
                                DefaultChemObjectBuilder.getInstance());
                        toPaste = sp.parseSmiles(
                                ((String) transfer.getTransferData(
                                        DataFlavor.stringFlavor)).trim());

                        IAtomContainerSet mols = ConnectivityChecker.partitionIntoMolecules(toPaste);
                        for(int i=0;i<mols.getAtomContainerCount();i++)
                        {
                            StructureDiagramGenerator sdg =
                                new StructureDiagramGenerator((IAtomContainer)mols.getAtomContainer(i));
                            sdg.generateCoordinates();
                        }
                        //SMILES parser sets valencies, unset
                        for(int i=0;i<toPaste.getAtomCount();i++){
                            toPaste.getAtom(i).setValency(null);
                        }
                    }
                } catch (Exception ex) {
                    jcpPanel.announceError(ex);
                    ex.printStackTrace();
                }
            }
            
            if (toPaste != null || rgrpQuery==true) {
//                jcpPanel.getRenderPanel().setZoomWide(true);
//                jcpPanel.get2DHub().getRenderer().getRenderer2DModel().setZoomFactor(1);
                if ( rgrpQuery==true) {
                	this.jcpPanel.setChemModel(chemModel);
                }
                else {
                	scaleStructure(toPaste);
                	insertStructure(toPaste, renderModel);
                }
            }
            else{
                JOptionPane.showMessageDialog(jcpPanel, GT.get("The content you tried to copy could not be read to any known format"), GT.get("Could not process content"), JOptionPane.WARNING_MESSAGE);
            }

        } else if (type.equals("cut")) {
            handleSystemClipboard(sysClip);
            IAtom atomInRange = null;
            IBond bondInRange = null;
            IChemObject object = getSource(e);
            logger.debug("Source of call: ", object);
            if (object instanceof IAtom) {
                atomInRange = (IAtom) object;
            } else {
                atomInRange = renderModel.getHighlightedAtom();
            }
            if (object instanceof IBond) {
                bondInRange = (IBond) object;
            } else {
                bondInRange = renderModel.getHighlightedBond();
            }
            IAtomContainer tocopyclone =
                jcpPanel.getChemModel().getBuilder().newInstance(IAtomContainer.class);
            if (atomInRange != null) {
                tocopyclone.addAtom(atomInRange);
                jcpPanel.get2DHub().removeAtom(atomInRange);
                renderModel.setHighlightedAtom(null);
            } else if (bondInRange != null) {
                tocopyclone.addBond(bondInRange);
                jcpPanel.get2DHub().removeBond(bondInRange);
            }else if(renderModel.getSelection()!=null && renderModel.getSelection().getConnectedAtomContainer()!=null){
                IChemObjectSelection selection = renderModel.getSelection();
                IAtomContainer selected = selection.getConnectedAtomContainer();
                tocopyclone.add(selected);
                jcpPanel.get2DHub().deleteFragment(new AtomBondSet(selected));
                renderModel.setSelection(new LogicalSelection(
                        LogicalSelection.Type.NONE));
                jcpPanel.get2DHub().updateView();
            }
			if(tocopyclone.getAtomCount()>0 || tocopyclone.getBondCount()>0)
			    addToClipboard(sysClip, tocopyclone);
			
        }else if (type.equals("selectAll")) {
            ControllerHub hub = jcpPanel.get2DHub();
            IChemObjectSelection allSelection =
                new LogicalSelection(LogicalSelection.Type.ALL);

            allSelection.select(hub.getIChemModel());
            renderModel.setSelection(allSelection);
            SelectSquareModule succusorModule = new SelectSquareModule(hub);
            succusorModule.setID("select");
            MoveModule newActiveModule = new MoveModule(hub, succusorModule);
            newActiveModule.setID("move");
            hub.setActiveDrawModule(newActiveModule);

        } else if (type.equals("selectFromChemObject")) {

        	// FIXME: implement for others than Reaction, Atom, Bond
        	IChemObject object = getSource(e);
            if (object instanceof IAtom) {
                SingleSelection<IAtom> container = new SingleSelection<IAtom>((IAtom)object);
                renderModel.setSelection(container);
            }
            else if (object instanceof IBond) {
                SingleSelection<IBond> container = new SingleSelection<IBond>((IBond)object);
                renderModel.setSelection(container);
            }
            else if (object instanceof IReaction) {
                IAtomContainer wholeModel =
                    jcpPanel.getChemModel().getBuilder().newInstance(IAtomContainer.class);
                for (IAtomContainer container :
                    ReactionManipulator.getAllAtomContainers(
                            (IReaction)object)) {
                    wholeModel.add(container);
                }
                ShapeSelection container = new RectangleSelection();
                for (IAtom atom : wholeModel.atoms()) {
                    container.atoms.add(atom);
                }
                for (IBond bond : wholeModel.bonds()) {
                    container.bonds.add(bond);
                }
                renderModel.setSelection(container);
            }
            else {
                logger.warn("Cannot select everything in : ", object);
            }
        } else if (type.equals("selectReactionReactants")) {
            IChemObject object = getSource(e);
            if (object instanceof IReaction) {
                IReaction reaction = (IReaction) object;
                IAtomContainer wholeModel =
                    jcpPanel.getChemModel().getBuilder().newInstance(IAtomContainer.class);
                for (IAtomContainer container :
                    MoleculeSetManipulator.getAllAtomContainers(
                            reaction.getReactants())) {
                        wholeModel.add(container);
                }
                ShapeSelection container = new RectangleSelection();
                for (IAtom atom : wholeModel.atoms()) {
                    container.atoms.add(atom);
                }
                for (IBond bond : wholeModel.bonds()) {
                    container.bonds.add(bond);
                }
                renderModel.setSelection(container);
            }
            else {
                logger.warn("Cannot select reactants from : ", object);
            }
        } else if (type.equals("selectReactionProducts")) {
            IChemObject object = getSource(e);
            if (object instanceof IReaction) {
                    IReaction reaction = (IReaction) object;
                    IAtomContainer wholeModel =
                        jcpPanel.getChemModel().getBuilder().newInstance(IAtomContainer.class);
                for (IAtomContainer container :
                    MoleculeSetManipulator.getAllAtomContainers(
                            reaction.getProducts())) {
                        wholeModel.add(container);
                }
                ShapeSelection container = new RectangleSelection();
                for (IAtom atom : wholeModel.atoms()) {
                    container.atoms.add(atom);
                }
                for (IBond bond : wholeModel.bonds()) {
                    container.bonds.add(bond);
                }
                renderModel.setSelection(container);
            }
            else {
                logger.warn("Cannot select reactants from : ", object);
            }
        }
        jcpPanel.get2DHub().updateView();

    }

    /**
     * Scale the structure to be pasted to the same scale of the current drawing 
     * @param topaste
     */
    private void scaleStructure (IAtomContainer topaste)  {
        double bondLengthModel = Renderer.calculateBondLength(jcpPanel.get2DHub().getIChemModel().getMoleculeSet());
        double bondLengthInsert = GeometryUtil.getBondLengthMedian(topaste);
        double scale=bondLengthModel/bondLengthInsert;
        for (IAtom atom : topaste.atoms()) {
            if (atom.getPoint2d()!=null) {
                atom.setPoint2d(new Point2d(atom.getPoint2d().x*scale,atom.getPoint2d().y*scale));
            }
        }
    }

    /**
     * Inserts a structure into the panel. It adds Hs if needed and highlights the structure after insert.
     * 
     * @param toPaste     The structure to paste.
     * @param renderModel The current renderer model.
     */
    private void insertStructure(IAtomContainer toPaste, JChemPaintRendererModel renderModel) {

    	//add implicit hs
        if(jcpPanel.get2DHub().getController2DModel().getAutoUpdateImplicitHydrogens()){
            try {
                AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(toPaste);
                CDKHydrogenAdder hAdder = CDKHydrogenAdder.getInstance(toPaste
                        .getBuilder());
                hAdder.addImplicitHydrogens(toPaste);
            } catch (CDKException ex) {
                ex.printStackTrace();
                // do nothing
            }
            //valencies are set when doing atom typing, which we don't want in jcp
            for(int i=0;i<toPaste.getAtomCount();i++){
                toPaste.getAtom(i).setValency(null);
            }
        }

        //somehow, in case of single atoms, there are no coordinates
        if(toPaste.getAtomCount()==1 && toPaste.getAtom(0).getPoint2d()==null)
            toPaste.getAtom(0).setPoint2d(new Point2d(0,0));

        try {
            JChemPaint.generateModel(jcpPanel, toPaste, false, true);
        } catch (CDKException e) {
			e.printStackTrace();
        	return;
        }
        jcpPanel.get2DHub().fireStructureChangedEvent();

        //We select the inserted structure
        IChemObjectSelection selection = new LogicalSelection(LogicalSelection.Type.ALL);
        selection.select(ChemModelManipulator.newChemModel(toPaste));
        renderModel.setSelection(selection);
        SelectSquareModule successorModule = new SelectSquareModule(jcpPanel.get2DHub());
        successorModule.setID("select");
        MoveModule newActiveModule = new MoveModule(jcpPanel.get2DHub(), successorModule);
        newActiveModule.setID("move");
        jcpPanel.get2DHub().setActiveDrawModule(newActiveModule);        
    }

    private void handleSystemClipboard(Clipboard clipboard) {
        Transferable clipboardContent = clipboard.getContents(this);
        DataFlavor flavors[]=clipboardContent.getTransferDataFlavors();
        String text = "System.clipoard content";
        for(int i=0;i<flavors.length;++i)
        {
            text+="\n\n Name: "+ flavors[i].getHumanPresentableName();
            text+="\n MIME Type: "+flavors[i].getMimeType();
            text+="\n Class: ";
            Class<?> cl = flavors[i].getRepresentationClass();
            if(cl==null) text+="null";
            else text+=cl.getName();
        }
        logger.debug(text);
    }

    class JcpSelection implements Transferable, ClipboardOwner {
        private DataFlavor [] supportedFlavors = {
                molFlavor, DataFlavor.stringFlavor, svgFlavor, cmlFlavor, smilesFlavor
        };
        String mol;
        String smiles;
        String svg;
        String cml;

        public JcpSelection(IAtomContainer tocopy1) {
            IAtomContainer tocopy= tocopy1.getBuilder().newInstance(IAtomContainer.class,tocopy1);
            // MDL mol output
            StringWriter sw = new StringWriter();
            try (MDLV2000Writer mdlWriter = new MDLV2000Writer(sw)) {
				mdlWriter.writeMolecule(tocopy);
			} catch (Exception ex) {
			    logger.error("Could not write molecule to string: ", ex.getMessage());
                logger.debug(ex);
			}
            this.mol=sw.toString();
            SmilesGenerator sg=SmilesGenerator.isomeric();
            try {
                smiles = sg.create(tocopy);
            } catch (CDKException ex) {
                logger.error("Could not create SMILES: ", ex.getMessage());
                logger.debug(ex);    
            }
            // SVG output
            svg=jcpPanel.getSVGString();
            // CML output
            sw = new StringWriter();
            Class<?> cmlWriterClass = null;
            try {
                cmlWriterClass = this.getClass().getClassLoader().loadClass(
                "org.openscience.cdk.io.CMLWriter");
                if (cmlWriterClass != null) {
                    IChemObjectWriter cow = (IChemObjectWriter)cmlWriterClass.newInstance();
                    Constructor<? extends IChemObjectWriter> constructor = cow.getClass().getConstructor(new Class[]{Writer.class});
                    cow = (IChemObjectWriter)constructor.newInstance(new Object[]{sw});
                    cow.write(tocopy);
                    cow.close();
                }
                cml=sw.toString();
            } catch (Exception exception) {
                logger.error("Could not load CMLWriter: ", exception.getMessage());
                logger.debug(exception);
            }
        }

        public synchronized DataFlavor [] getTransferDataFlavors () {
            return (supportedFlavors);
        }

        public boolean isDataFlavorSupported (DataFlavor parFlavor) {
            for(int i=0;i<supportedFlavors.length;i++){
                if(supportedFlavors[i].equals(parFlavor))
                    return true;
            }
            return false;
        }

        public synchronized Object getTransferData (DataFlavor parFlavor)	throws UnsupportedFlavorException {
            if (parFlavor.equals (molFlavor)) {
                return new StringReader(mol);
            } else if (parFlavor.equals (smilesFlavor)) {
                return new StringReader(smiles);
            } else if(parFlavor.equals(DataFlavor.stringFlavor)) {
                return mol;
            } else if(parFlavor.equals(cmlFlavor)) {
                return new StringReader(cml);
            } else if(parFlavor.equals(svgFlavor)) {
                return new StringReader(svg);
            } else {
                throw new UnsupportedFlavorException (parFlavor);
            }
        }

        public void lostOwnership (Clipboard parClipboard, Transferable parTransferable) {
            System.out.println ("Lost ownership");
        }
    }

    class SmilesSelection implements Transferable, ClipboardOwner {
        private DataFlavor [] supportedFlavors = {
                DataFlavor.stringFlavor
        };

        String smiles;

        public SmilesSelection(String smiles) throws Exception {
            this.smiles = smiles;
        }

        public synchronized DataFlavor [] getTransferDataFlavors () {
            return (supportedFlavors);
        }

        public boolean isDataFlavorSupported (DataFlavor parFlavor) {
            for(int i=0;i<supportedFlavors.length;i++){
                if(supportedFlavors[i].equals(parFlavor))
                    return true;
            }
            return false;
        }

        public synchronized Object getTransferData (DataFlavor parFlavor)	throws UnsupportedFlavorException {
            if(parFlavor.equals(DataFlavor.stringFlavor)) {
                return smiles;
            } else {
                throw new UnsupportedFlavorException (parFlavor);
            }
        }

        public void lostOwnership (Clipboard parClipboard, Transferable parTransferable) {
            System.out.println ("Lost ownership");
        }
    }
}

