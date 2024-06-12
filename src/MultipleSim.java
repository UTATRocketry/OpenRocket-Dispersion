import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import net.sf.openrocket.document.OpenRocketDocument;
import net.sf.openrocket.document.Simulation;
import net.sf.openrocket.file.CSVExport;
import net.sf.openrocket.file.GeneralRocketLoader;
import net.sf.openrocket.file.RocketLoadException;
import net.sf.openrocket.gui.util.GUIUtil;
import net.sf.openrocket.gui.util.SwingPreferences;
import net.sf.openrocket.logging.LoggingSystemSetup;
import net.sf.openrocket.plugin.PluginModule;

import net.sf.openrocket.rocketcomponent.Rocket;
import net.sf.openrocket.simulation.*;

import net.sf.openrocket.simulation.customexpression.CustomExpression;
import net.sf.openrocket.simulation.customexpression.CustomExpressionSimulationListener;
import net.sf.openrocket.simulation.exception.SimulationException;
import net.sf.openrocket.startup.Application;
import net.sf.openrocket.startup.GuiModule;

import net.sf.openrocket.unit.Unit;
import net.sf.openrocket.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import static net.sf.openrocket.simulation.FlightDataType.*;
import static net.sf.openrocket.unit.UnitGroup.*;

public class MultipleSim {

    double windspeed = 0;
    double deviation = 0;

    final String parentfolder = "C:\\Users\\willt\\OneDrive - University of Toronto\\UTAT\\OpenRocket\\";
    OpenRocketDocument document;
    List<CustomExpression> cexplist;

    List<FlightDataType> fields = new ArrayList<>();
    List<Unit> units = new ArrayList<>();

    private static final Logger log = LoggerFactory.getLogger(MultipleSim.class);

    public static void main(String args[]) throws Exception {
        new MultipleSim().startup();
    }

    private void portOpenRocket() {
        LoggingSystemSetup.setupLoggingAppender();
        LoggingSystemSetup.addConsoleAppender();

        // Load motors etc.
        log.info("Loading databases");

        GuiModule guiModule = new GuiModule();
        Module pluginModule = new PluginModule();
        Injector injector = Guice.createInjector(guiModule, pluginModule);
        Application.setInjector(injector);

        guiModule.startLoader();

        // Set the best available look-and-feel
        log.info("Setting best LAF");
        GUIUtil.setBestLAF();

        // Load defaults
        ((SwingPreferences) Application.getPreferences()).loadDefaultUnits();
    }

    private OpenRocketDocument loadDocument(String filename) throws RocketLoadException {
        return new GeneralRocketLoader(new File(filename)).load();
    }

    private void addDefaultTypes() {
        fields.add(TYPE_TIME);
        units.add(UNITS_FLIGHT_TIME.getUnit("s"));

        fields.add(TYPE_ALTITUDE);
        units.add(UNITS_LENGTH.getUnit("m"));

        fields.add(TYPE_POSITION_X);
        units.add(UNITS_LENGTH.getUnit("m"));

        fields.add(TYPE_POSITION_Y);
        units.add(UNITS_LENGTH.getUnit("m"));

        /*
        fields.add(TYPE_STABILITY);
        units.add(UNITS_STABILITY_CALIBERS.getDefaultUnit());

        fields.add(TYPE_DRAG_COEFF);
        units.add(UNITS_COEFFICIENT.getDefaultUnit());

        fields.add(TYPE_MACH_NUMBER);
        units.add(UNITS_COEFFICIENT.getDefaultUnit());

        fields.add(TYPE_AOA);
        units.add(UNITS_ANGLE.getDefaultUnit());

         */
    }

    private List<CustomExpression> addCustomExpressions(OpenRocketDocument document) {
        CustomExpression Dynamic_Pressure = new CustomExpression(
                document,
                "Dynamic Pressure",
                "Q",
                "Pa",
                "0.5 * Vt^2 * P / (T*287)"
        );
        Dynamic_Pressure.addToDocument();

        List<CustomExpression> cexplist = new ArrayList<>();
        cexplist.add(Dynamic_Pressure);

        fields.add(Dynamic_Pressure.getType());
        units.add(UNITS_PRESSURE.getUnit("Pa"));

        return cexplist;
    }

    private void addSimulationOptions(Simulation sim) {
        final SimulationOptions simops = new SimulationOptions();
        simops.setWindSpeedAverage(windspeed);
        simops.setWindTurbulenceIntensity(deviation);
        simops.setLaunchRodLength(6);
        simops.setLaunchRodAngle(5);
        simops.setLaunchIntoWind(true);

        sim.copySimulationOptionsFrom(simops);
    }

    private void exportCSV(
            FileOutputStream writefile, Simulation sim,
            FlightDataType[] flightDataTypes, Unit[] units)
            throws IOException {
        CSVExport.exportCSV(writefile, sim, sim.getSimulatedData().getBranch(0),
                flightDataTypes, units, ",", 3, false,
                "#", false, true, false);
    }

    private OpenRocketDocument openDocument(String rocketfile) throws RocketLoadException {
        return loadDocument(parentfolder+rocketfile);
    }

    private void runSimulation(String datafile) throws SimulationException, IOException {
        final Rocket rocket = document.getRocket();

        final FileOutputStream writefile = new FileOutputStream(parentfolder+datafile);
        final Simulation sim = new Simulation(document, rocket);

        addSimulationOptions(sim);
        sim.simulate(new CustomExpressionSimulationListener(cexplist));
        FlightDataType[] fields_arr = new FlightDataType[fields.size()];
        Unit[] units_arr = new Unit[units.size()];
        exportCSV(writefile, sim, fields.toArray(fields_arr), units.toArray(units_arr));
    }

    private void startup() throws Exception {
        portOpenRocket();
        document = openDocument("Defiance_OR_fillbay.ork");
        addDefaultTypes();
        cexplist = addCustomExpressions(document);

        windspeed = 0;
        deviation = 0;

        LocalDateTime datetime = java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        String date_string = datetime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String time_string = datetime.format(DateTimeFormatter.ofPattern("HH-mm"));
        String directory_s = "results\\" + date_string + "\\" + time_string;

        new File(parentfolder + directory_s).mkdirs();
        for (int i = 0; i < 2; i++) {
            runSimulation(String.format("%s\\%d.csv", directory_s, i));
            windspeed += 0.5;
            deviation += 0.1;
        }
    }
}