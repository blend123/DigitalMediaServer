# Reporting an issue

In order to help diagnose and fix your issue we usually need your "debug information". To generate it, follow these steps:

* Start Digital Media Server (DMS).
* Go to the `Logs` tab.
* Press the `Create TRACE logs` button, which will restart DMS in the TRACE mode. If that doesn't work for some reason, either select `Trace` from the `Log Level` dropdown at the bottom of the screen or set `log_level = TRACE` in `DMS.conf` and restart DMS manually.
* Reproduce the bug.
* Click `Pack debug files` on the lower left.
* Click `Zip selected files`.
* Save the zip file to a location you will remember.
* Attach the zip file in the corresponding GitHub issue.

If it is a new bug that wasn't there in a previous version, we will often request debug information from the last version that did not have the bug too. If that is the case, please repeat the above steps for the last version without the bug as well.

Thanks for your efforts in helping us make Digital Media Server better!

# Creating a Pull Request

We welcome Pull Requests. Please be descriptive about the purpose of the code change, and please try to use the code conventions we use.

# <a name="Attachments"></a>Attachments

GitHub only allows a few attachment extensions: `.png`, `.gif`, `.jpg`, `.docx`, `.pptx`, `.xlsx`, `.txt`, `.pdf`, `.zip` and `.gz`. Other attachments can be zipped and attached here. Text files can also simply have a `.txt` extension can be added to their file name.

GitHub has a size limit of 20 MB for attachments. If your attachment is too big, upload your attachment somewhere else (for example [wikisend.com](http://wikisend.com)) and post the link in the GitHub issue. If you upload your attachment elsewhere, please avoid sites with restricted access or intrusive ads.
