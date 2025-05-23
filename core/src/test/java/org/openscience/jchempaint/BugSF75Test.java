package org.openscience.jchempaint;

import java.awt.Point;
import java.io.IOException;

import javax.vecmath.Point2d;

import org.fest.swing.core.MouseButton;
import org.fest.swing.fixture.JPanelFixture;
import org.junit.Assert;
import org.junit.Test;
import org.openscience.cdk.exception.CDKException;

public class BugSF75Test extends AbstractAppletTest {

    @Test public void testBug75() throws CDKException, ClassNotFoundException, IOException, CloneNotSupportedException{
        JPanelFixture jcppanel=applet.panel("appletframe");
        JChemPaintPanel panel = (JChemPaintPanel)jcppanel.target;
        applet.button("hexagon").target.doClick();
        applet.click();
        applet.panel("renderpanel").robot.click(applet.panel("renderpanel").component(), new Point(100,100), MouseButton.LEFT_BUTTON,1);
        applet.button("eraser").target.doClick();
        Point2d moveto=getAtomPoint(panel,0,1);
        applet.panel("renderpanel").robot.click(applet.panel("renderpanel").component(), new Point((int)moveto.x, (int)moveto.y), MouseButton.LEFT_BUTTON,1);
        moveto=getAtomPoint(panel,0,1);
        applet.panel("renderpanel").robot.click(applet.panel("renderpanel").component(), new Point((int)moveto.x, (int)moveto.y), MouseButton.LEFT_BUTTON,1);
        moveto=getAtomPoint(panel,0,1);
        applet.panel("renderpanel").robot.click(applet.panel("renderpanel").component(), new Point((int)moveto.x, (int)moveto.y), MouseButton.LEFT_BUTTON,1);
        moveto=getAtomPoint(panel,0,1);
        applet.panel("renderpanel").robot.click(applet.panel("renderpanel").component(), new Point((int)moveto.x, (int)moveto.y), MouseButton.LEFT_BUTTON,1);
        moveto=getAtomPoint(panel,0,1);
        applet.panel("renderpanel").robot.click(applet.panel("renderpanel").component(), new Point((int)moveto.x, (int)moveto.y), MouseButton.LEFT_BUTTON,1);
        moveto=getAtomPoint(panel,0,1);
        applet.panel("renderpanel").robot.click(applet.panel("renderpanel").component(), new Point((int)moveto.x, (int)moveto.y), MouseButton.LEFT_BUTTON,1);
        Assert.assertEquals("C1CCCCC1", panel.getSmiles());
        restoreModelToEmpty();
    }

}
