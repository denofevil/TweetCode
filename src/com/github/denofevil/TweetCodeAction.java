package com.github.denofevil;

import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.mac.foundation.ID;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.media.ImageUpload;
import twitter4j.media.ImageUploadFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static com.intellij.ui.mac.foundation.Foundation.*;

/**
 * @author Dennis.Ushakov
 */
public class TweetCodeAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(TweetCodeAction.class);

  public TweetCodeAction() {
    super(null, null, IconLoader.findIcon("/twitter-bird-light-bgs.png"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getRequiredData(LangDataKeys.PROJECT);
    final Editor editor = e.getRequiredData(LangDataKeys.EDITOR);
    final Document document = editor.getDocument();
    final SelectionModel selectionModel = editor.getSelectionModel();

    final int start = selectionModel.getSelectionStart();
    final int end = selectionModel.getSelectionEnd();

    final int startLine = document.getLineNumber(start);
    final int endLine = document.getLineNumber(end) + 1;
    selectionModel.removeSelection();
    final EditorFragmentComponent fragment = EditorFragmentComponent.createEditorFragmentComponent(editor, startLine, endLine, false, false);
    try {
      final byte[] picture = createPicture(fragment);
      if(picture != null) {
        if(SystemInfo.isMacOSMountainLion) {
          doMountainLionTweet(project, picture);
        } else {
          doTweet(project, picture);
        }
      }
    } catch (Exception exception) {
      LOG.error(exception);
    }
    selectionModel.setSelection(start, end);
  }

	private void doMountainLionTweet(Project project, byte[] imageBytes) throws Exception {
		try {
			ID sharingService = invoke(getObjcClass("NSSharingService"), "sharingServiceNamed:", nsString("com.apple.share.Twitter.post"));
			if(!sharingService.equals(ID.NIL)) {
				final ID imageData = invoke(invoke("NSData", "alloc"), "initWithBytes:length:", imageBytes, imageBytes.length);
				final ID nsImage = invoke(invoke("NSImage", "alloc"), "initWithData:", imageData);
				final ID shareItems = invoke(invoke("NSArray", "alloc"), "initWithObjects:", nsImage);
				invoke(sharingService, "performWithItems:", shareItems);
			} else {
				//fallback
				doTweet(project, imageBytes);
			}
		} catch (IOException e) {
			LOG.error(e);
			Messages.showErrorDialog(project, "Failed to tweet code", "Can't Tweet :(");
		}
	}

	private void doTweet(Project project, final byte[] imageBytes) throws Exception {
    final Twitter twitter = authorize(project);
    if (twitter == null) {
      Messages.showErrorDialog(project, "Failed to authorize with twitter", "Can't Tweet :(");
      return;
    }
    final ImageUploadFactory factory = new ImageUploadFactory();
    final ImageUpload imageUpload = factory.getInstance(twitter.getAuthorization());
    final String text = Messages.showInputDialog(project, "Enter optional comment", "Tweet Code", null, "", new InputValidator() {
      @Override
      public boolean checkInput(String s) {
        return canClose(s);
      }

      @Override
      public boolean canClose(String s) {
        return s.length() <= 108;
      }
    });
    if (text == null) return;
    imageUpload.upload("CodeFragment", new ByteArrayInputStream(imageBytes), text);
  }

  private byte[] createPicture(EditorFragmentComponent fragment) {
    final BufferedImage image = createBufferedImage(fragment);
	  final ByteArrayOutputStream stream = new ByteArrayOutputStream();
	  try {
		  ImageIO.write(image, "png", stream);
	  } catch (IOException e) {
		  LOG.error(e);
		  return null;
	  }
	  return stream.toByteArray();
  }

	private BufferedImage createBufferedImage(EditorFragmentComponent fragment) {
		final Dimension size = fragment.getPreferredSize();
		fragment.setSize(size);
		fragment.doLayout();
        //noinspection UndesirableClassUsage
        final BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
		final Graphics graphics = image.getGraphics();
		UISettings.setupAntialiasing(graphics);
		fragment.printAll(graphics);
		graphics.dispose();
		return image;
	}

  private Twitter authorize(final Project project) throws TwitterException, IOException, URISyntaxException {
    final Twitter twitter = new TwitterFactory().getInstance();
    twitter.setOAuthConsumer("pOgq22k0cMP5sHy9mDIGQ", "x7FZAMwheKbPHe3f05TLEMRbpDp9f1rGKoDbxDBE");
    final TwitterSettings settings = TwitterSettings.getInstance();
    final AccessToken accessToken;
    if (settings.myToken == null) {
      final RequestToken requestToken = twitter.getOAuthRequestToken();
      Desktop.getDesktop().browse(new URI(requestToken.getAuthorizationURL()));
      final String pin = Messages.showInputDialog(project, "Enter PIN", "Twitter Authorization", null);
      if (pin != null) {
        if (StringUtil.isEmpty(pin)) {
          accessToken = twitter.getOAuthAccessToken(requestToken);
        } else {
          accessToken = twitter.getOAuthAccessToken(requestToken, pin);
        }
        settings.myToken = accessToken.getToken();
        settings.mySecret = accessToken.getTokenSecret();
      } else {
        return null;
      }
    } else {
      accessToken = new AccessToken(settings.myToken, settings.mySecret);
    }
    twitter.setOAuthAccessToken(accessToken);
    return twitter;
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(LangDataKeys.PROJECT);
    final Editor editor = e.getData(LangDataKeys.EDITOR);
    e.getPresentation().setVisible(project != null && editor != null && editor.getSelectionModel().hasSelection());
  }
}
