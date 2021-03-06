package com.github.mars05.crud.intellij.plugin.action;

import com.github.mars05.crud.intellij.plugin.util.CrudUtils;
import com.github.mars05.crud.intellij.plugin.util.PsiFileUtils;
import com.github.mars05.crud.intellij.plugin.util.Selection;
import com.github.mars05.crud.intellij.plugin.util.SelectionContext;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static com.intellij.openapi.module.ModuleUtilCore.findModuleForFile;

/**
 * @author xiaoyu
 */
public class NewFileAction extends AnAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewFileAction.class);

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile virtualFile = e.getData(DataKeys.VIRTUAL_FILE);
        if (!virtualFile.isDirectory()) {
            virtualFile = virtualFile.getParent();
        }
        Module module = findModuleForFile(virtualFile, project);

        // 项目绝对路径
        String moduleRootPath = ModuleRootManager.getInstance(module).getContentRoots()[0].getPath();

        String basePackage = getBasePackage(project, moduleRootPath);
        SelectionContext.clearAllSet();

        SelectionContext.setPackage(basePackage);
        if (StringUtils.isNotBlank(basePackage)) {
            basePackage += ".";
        }
        SelectionContext.setControllerPackage(basePackage + "web.controller");
        SelectionContext.setServicePackage(basePackage + "service");
        SelectionContext.setDaoPackage(basePackage + "dao");
        SelectionContext.setModelPackage(basePackage + "pojo.entity");
        SelectionContext.setMapperDir(moduleRootPath + "/src/main/resources/mapper");

        CrudActionDialog dialog = new CrudActionDialog(project, module);
        if (!dialog.showAndGet()) {
            return;
        }
        DumbService.getInstance(project).runWhenSmart((DumbAwareRunnable) () -> new WriteCommandAction(project) {
            @Override
            protected void run(@NotNull Result result) {
                Selection selection = SelectionContext.copyToSelection();
                SelectionContext.clearAllSet();
                try {
                    PsiFileUtils.createCrud(project, selection, moduleRootPath);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                //解决依赖
                MavenProjectsManager.getInstance(project).forceUpdateAllProjectsOrFindAllAvailablePomFiles();
                //优化生成的所有Java类
                CrudUtils.doOptimize(project);
            }
        }.execute());
    }

    private String getBasePackage(Project project, String moduleRootPath)  {

        try (FileInputStream fis = new FileInputStream(new File(moduleRootPath + File.separator + "pom.xml"))) {

            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(fis);
            String artifactId = model.getArtifactId();
            String groupId = model.getGroupId();

            return groupId + "." + artifactId;
        } catch (IOException | XmlPullParserException e) {
            LOGGER.error("读取pom文件失败", e);
            Messages.showWarningDialog(project, "pom.xml读取失败", "错误");
            throw new IllegalArgumentException();
        }
    }

}
