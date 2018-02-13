# SCAVI - Smart Camera App for Visually Impaired

### Prerequisites
1. Android mobile with tango
2. Android studio

### Getting started
1. Download the Zip from the Github using  "Clone or download" button and unzip it. 
2. Open Android Studio -> File -> New Project -> Import Project and select the newly unzipped folder -> press OK

### Editing the code
Two classes 'SCAVIActivity' and 'SCAVIView' are used in this project and they can be found in app -> java -> com.n_san.scavi
1. SCAVIActivity - Main class that gets the point cloud data and processes it
2. SCAVIView - Creates the output rectangles in the mobile display

### Connecting the tango device
1. Install the usb drivers of the mobile model
2. Enable usb debugging in the mobile and connect it to the computer

### Running the app in the mobile
1. Open the SCAVIActivity class
2. Press the green colored play button (Run button) found in the top right side.
3. In the 'Select Deployment Target' dialog box that appears, select your connected mobile under 'Connected Devices' and press OK

## Authors

* **Santhanakrishnan Narayanan** - *Initial work* - [nsanthanakrishnan](https://github.com/nsanthanakrishnan)

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details
